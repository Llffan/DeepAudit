package com.deepaudit.service;

import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.persistence.repository.MedicalRecordMainRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * langchain4j @Tool 封装——供 AI Agent 调用 Python sample_pdf 脚本生成
 * 病案首页测试 PDF（含质控陷阱注入）。
 *
 * <p>脚本位于 {@code scripts/sample_pdf/}（相对于 deepaudit.scripts-dir，
 * 默认 {@code ../scripts}，即后端工作目录的上级目录）。
 * 输出写入 {@code deepaudit.sample-pdf.output-dir}（默认 {@code data/samples}，
 * 相对于项目根目录）。
 *
 * <p>此类需要被注入到 {@code AiServices.builder(...).tools(samplePdfTool)} 才能
 * 被 LLM 实际调用；单独注册为 Spring Bean 不会自动生效。
 */
@Component
public class SamplePdfTool {

    private static final Logger log = LoggerFactory.getLogger(SamplePdfTool.class);

    private static final List<String> AVAILABLE_TRAPS = List.of(
        "R001", "R002", "gender_obstetric", "cost_sum", "age_birth_weight", "main_dx_pathology"
    );

    private final Path scriptsDir;
    private final Path outputDir;
    private final MedicalRecordMainRepository recordRepository;
    private final ObjectMapper objectMapper;

    public SamplePdfTool(
        @Value("${deepaudit.scripts-dir:../scripts}") String scriptsDirProp,
        @Value("${deepaudit.sample-pdf.output-dir:../data/samples}") String outputDirProp,
        MedicalRecordMainRepository recordRepository,
        ObjectMapper objectMapper
    ) {
        this.scriptsDir = Paths.get(scriptsDirProp).toAbsolutePath().normalize();
        this.outputDir  = Paths.get(outputDirProp).toAbsolutePath().normalize();
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
        log.info("SamplePdfTool scripts={} output={}", this.scriptsDir, this.outputDir);
    }

    @Tool("列出所有可注入的质控陷阱（trap）名称和说明")
    public String listTraps() {
        return """
            可用陷阱（trap）列表：
            - R001            : 有主手术编码但清空术者和手术日期，触发 R001 手术信息完整性规则
            - R002            : 住院天数 +7 与入出院日期差不一致，触发 R002 逻辑规则
            - gender_obstetric: 男性 + 单胎顺产诊断，跨字段性别/产科一致性错误
            - cost_sum        : 总费用降至分项合计的 60%，费用合计校验错误
            - age_birth_weight: 年龄=0 但出生体重为空，新生儿条件必填规则错误
            - main_dx_pathology: C78 肿瘤主诊但无病理诊断，肿瘤条件必填规则错误
            """;
    }

    @Tool("生成单张病案首页测试 PDF，可选注入一个质控陷阱")
    public String generateSamplePdf(
        @P("要注入的陷阱名（R001/R002/gender_obstetric/cost_sum/age_birth_weight/main_dx_pathology），传空字符串生成干净病案")
        String trap,
        @P("随机种子保证可复现，传 -1 则每次随机")
        int seed
    ) {
        if (!trap.isBlank() && !AVAILABLE_TRAPS.contains(trap)) {
            return "错误：未知陷阱 " + trap + "，可用值：" + AVAILABLE_TRAPS;
        }

        List<String> cmd = new ArrayList<>(List.of(
            "python", "-m", "sample_pdf.cli",
            "--count", "1",
            "--out-dir", outputDir.toString()
        ));
        if (!trap.isBlank()) {
            cmd.addAll(List.of("--error-rate", "1.0", "--traps", trap));
        }
        if (seed >= 0) {
            cmd.addAll(List.of("--seed", String.valueOf(seed)));
        }
        return run(cmd);
    }

    @Tool("批量生成病案首页测试 PDF，按 errorRate 随机注入陷阱")
    public String batchGenerateSamplePdfs(
        @P("生成数量，建议 1-20")
        int count,
        @P("每张 PDF 注入陷阱的概率，0.0 = 全干净，1.0 = 全注入")
        double errorRate,
        @P("随机种子，-1 则每次随机")
        int seed
    ) {
        if (count < 1 || count > 50) {
            return "错误：count 必须在 1-50 之间";
        }

        List<String> cmd = new ArrayList<>(List.of(
            "python", "-m", "sample_pdf.cli",
            "--count", String.valueOf(count),
            "--out-dir", outputDir.toString(),
            "--error-rate", String.valueOf(errorRate)
        ));
        if (seed >= 0) {
            cmd.addAll(List.of("--seed", String.valueOf(seed)));
        }
        return run(cmd);
    }

    @Tool("根据数据库中已有的病案首页记录导出 PDF，recordId 为病案主键")
    public String exportRecordAsPdf(
        @P("病案主键 ID（medical_record_main.id）")
        long recordId
    ) {
        MedicalRecordMain r = recordRepository.findById(recordId)
            .orElseThrow(() -> new NotFoundException("病案 #" + recordId + " 不存在"));

        // HQMS 短码映射，来自 scripts/sample_pdf/spec.py V1_TO_HQMS 字典
        // （以 docs/病案首页与质控业务/病案标准.xlsx 为权威源）。
        // 字段顺序按表单分组：识别 → 人口学 → 联系 → 入出院 → 门急诊诊断 → 主诊 → 主术 → 费用 → 机构。
        Map<String, String> fields = new LinkedHashMap<>();

        // 识别
        put(fields, "BAH",          r.getRecordNo());
        put(fields, "XM",           r.getName());
        put(fields, "XB",           r.getGender());
        put(fields, "NL",           r.getAge());
        put(fields, "CSRQ",         r.getBirthDate());
        put(fields, "SFZH",         r.getIdCardMasked());

        // V6 — 人口学扩展（idCardType 在 HQMS 标准中无专属字段，故不导出）
        put(fields, "GJ",           r.getNationality());
        put(fields, "CSD",          r.getBirthPlace());
        put(fields, "GG",           r.getNativePlace());
        put(fields, "MZ",           r.getEthnicity());
        put(fields, "ZY",           r.getOccupation());
        put(fields, "HY",           r.getMaritalStatus());

        // V6 — 新生儿信息（1年内）：出生体重 / 入院体重 / 不足1岁年龄(天)
        // 与前端表单"新生儿信息"区块字段集对齐。
        put(fields, "XSETZ",        r.getNewbornBirthWeight());
        put(fields, "XSERYTZ",      r.getNewbornAdmissionWeight());
        put(fields, "BZYZS_NL",     r.getAgeDays());

        // V6 — 联系方式
        put(fields, "XZZ",          r.getCurrentAddress());
        put(fields, "DH",           r.getCurrentPhone());
        put(fields, "YB1",          r.getCurrentZip());
        put(fields, "HKDZ",         r.getRegisteredAddress());
        put(fields, "YB2",          r.getRegisteredZip());
        put(fields, "GZDWJDZ",      r.getWorkplace());
        put(fields, "DWDH",         r.getWorkPhone());
        put(fields, "YB3",          r.getWorkZip());
        put(fields, "LXRXM",        r.getContactName());
        put(fields, "GX",           r.getContactRelation());
        put(fields, "DZ",           r.getContactAddress());
        put(fields, "DH1",          r.getContactPhone());

        // 入出院
        put(fields, "RYSJ",         r.getAdmissionDate());
        put(fields, "CYSJ",         r.getDischargeDate());
        put(fields, "SJZY",         r.getLengthOfStay());
        put(fields, "RYKB",         r.getAdmissionDept());
        put(fields, "RYBF",         r.getAdmissionWard());
        put(fields, "ZKKB",         r.getSpecialtyDept());
        put(fields, "CYKB",         r.getDischargeDept());
        put(fields, "CYBF",         r.getDischargeWard());
        put(fields, "RYTJ",         r.getAdmissionRoute());
        put(fields, "LYFS",         r.getDischargeStatus());

        // 门(急)诊诊断（写到西医诊断槽）
        put(fields, "MZZD_XYZD",    r.getOutpatientDiagnosis());
        put(fields, "JBBM",         r.getOutpatientDiagnosisCode());

        // 出院主诊
        put(fields, "ZYZD_JBBM",    r.getMainDiagnosisCode());
        put(fields, "ZYZD",         r.getMainDiagnosisName());
        put(fields, "XY_RYBQ",      r.getMainAdmissionCondition()); // V7 — 主诊入院病况
        put(fields, "BLZD",         r.getPathologicalDiagnosis());

        // V9 supplementary — 损伤、中毒
        put(fields, "SSZDWBYS",     r.getInjuryPoisoningCause());
        put(fields, "SSZDWBYS_BM",  r.getInjuryPoisoningCode());

        // V9 supplementary — 病理（诊断名沿用 BLZD 上方已写）
        put(fields, "BLZD_BM",      r.getPathologicalDiagnosisCode());
        put(fields, "BLH",          r.getPathologyNumber());

        // V9 supplementary — 过敏 / 尸检 / 血型
        put(fields, "YWGM",         r.getDrugAllergy());
        put(fields, "GMYW",         r.getAllergyDrugs());
        put(fields, "SJ",           r.getAutopsy());
        put(fields, "XX",           r.getBloodType());
        put(fields, "RH",           r.getRhBloodType());

        // V9 supplementary — 医生
        put(fields, "KZR",          r.getDepartmentDirector());
        put(fields, "ZRYS",         r.getChiefPhysician());
        put(fields, "ZZYS",         r.getAttendingPhysician());
        put(fields, "ZYYS",         r.getResidentPhysician());
        put(fields, "ZRHS",         r.getResponsibleNurse());
        put(fields, "JXYS",         r.getTraineePhysician());
        put(fields, "SXYS",         r.getInternPhysician());
        put(fields, "BMY",          r.getCoder());

        // V9 supplementary — 质控
        put(fields, "BAZL",         r.getRecordQuality());
        put(fields, "ZKYS",         r.getQcPhysician());
        put(fields, "ZKHS",         r.getQcNurse());
        put(fields, "ZKRQ",         r.getQcDate());

        // 机构
        put(fields, "JGMC",         r.getSourceHospital());

        String fieldsJson;
        try {
            fieldsJson = objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            return "序列化失败：" + e.getMessage();
        }

        List<String> cmd = new ArrayList<>(List.of(
            "python", "-m", "sample_pdf.cli",
            "--out-dir", outputDir.toString(),
            "--prefix", "record_" + recordId,
            "--fields-json", fieldsJson
        ));
        return run(cmd);
    }

    private static void put(Map<String, String> map, String key, Object value) {
        if (value != null) {
            map.put(key, value.toString());
        }
    }

    // -------------------------------------------------------------------------

    private String run(List<String> command) {
        log.info("SamplePdfTool running: {}", String.join(" ", command));
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(scriptsDir.toFile());
            // 让 Python 找到 sample_pdf 包（scripts/ 已是 cwd，但显式加更安全）
            pb.environment().merge(
                "PYTHONPATH",
                scriptsDir.toString(),
                (old, added) -> old + File.pathSeparator + added
            );
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            String output;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                output = r.lines().collect(Collectors.joining("\n"));
            }
            int exit = proc.waitFor();
            if (exit != 0) {
                log.error("sample_pdf script exit={} output={}", exit, output);
                return "脚本执行失败（exit " + exit + "）：" + output;
            }
            log.info("sample_pdf script done: {}", output);
            return output.isBlank() ? "完成，输出目录：" + outputDir : output;
        } catch (Exception e) {
            log.error("SamplePdfTool execution error", e);
            return "执行失败：" + e.getMessage();
        }
    }
}
