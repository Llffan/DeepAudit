// Mirrors V1 + V6 + V7 + V8 + V9 `medical_record_main` columns + diagnosis subtable.
// camelCase per Spring Data JPA SnakeCaseStrategy default mapping.
//
// V9 重构（2026-05-05）：
//  - DROP 主要手术 5 字段（mainOperationCode/Name/operationDate/operator/anesthesiaMethod）
//  - DROP 费用 4 字段（totalCost/drugCost/operationCost/medicalServiceCost）
//  - ADD 损伤中毒外因 / 病理诊断扩展 / 药物过敏 / 血型 / 8 类医生 / 质控 共 21 个字段

export interface Diagnosis {
  diagType: 'main' | 'other';
  seqNo: number | null;
  diagnosisName: string;
  diagnosisCode: string | null;
  icdVersion: string | null;
  admissionCondition: string | null;
  dischargeCondition: string | null;
  note: string | null;
}

export function emptyOtherDiagnosis(seqNo: number): Diagnosis {
  return {
    diagType: 'other',
    seqNo,
    diagnosisName: '',
    diagnosisCode: null,
    icdVersion: 'ICD-10',
    admissionCondition: null,
    dischargeCondition: null,
    note: null,
  };
}

export interface MedicalRecord {
  id?: number;

  // Identity (6)
  recordNo: string;
  name: string | null;
  gender: string | null;
  birthDate: string | null;
  age: number | null;
  idCardMasked: string | null;

  // V6 — Demographics (10)
  nationality: string | null;
  ethnicity: string | null;
  maritalStatus: string | null;
  occupation: string | null;
  ageDays: number | null;
  newbornBirthWeight: number | null;
  newbornAdmissionWeight: number | null;
  idCardType: string | null;
  birthPlace: string | null;
  nativePlace: string | null;

  // V6 — Address & contacts (12)
  currentAddress: string | null;
  currentPhone: string | null;
  currentZip: string | null;
  registeredAddress: string | null;
  registeredZip: string | null;
  workplace: string | null;
  workPhone: string | null;
  workZip: string | null;
  contactName: string | null;
  contactRelation: string | null;
  contactAddress: string | null;
  contactPhone: string | null;

  // Admission / discharge (7)
  admissionDate: string | null;
  dischargeDate: string | null;
  lengthOfStay: number | null;
  admissionDept: string | null;
  dischargeDept: string | null;
  admissionRoute: string | null;
  dischargeStatus: string | null;

  // V6 — Ward / specialty (3)
  admissionWard: string | null;
  dischargeWard: string | null;
  specialtyDept: string | null;

  // V6 — Outpatient diagnosis (2) + V8 extras (2)
  outpatientDiagnosis: string | null;
  outpatientDiagnosisCode: string | null;
  outpatientAdmissionCondition: string | null;
  confirmedAfterAdmissionDate: string | null;

  // Diagnoses — main flattened (8: V1 5 + V7 3)
  mainDiagnosisCode: string | null;
  mainDiagnosisName: string | null;
  mainDiagnosisIcdVer: string | null;
  mainAdmissionCondition: string | null;
  mainDischargeCondition: string | null;
  mainNote: string | null;
  otherDiagnosisCount: number | null;

  // V7 — Other diagnoses (subtable rows; main diagnosis NOT included here)
  diagnoses: Diagnosis[];

  // ───── V9 supplementary fields（非主表主要字段，不参与缺项检查）─────

  // 损伤、中毒（2）
  injuryPoisoningCause: string | null;
  injuryPoisoningCode: string | null;

  // 病理（3：诊断名沿用 V1 旧列 pathologicalDiagnosis，新增编码与病理号）
  pathologicalDiagnosis: string | null;
  pathologicalDiagnosisCode: string | null;
  pathologyNumber: string | null;

  // 过敏 / 尸检 / 血型（5）
  drugAllergy: string | null;       // 无 / 有
  allergyDrugs: string | null;
  autopsy: string | null;           // 是 / 否
  bloodType: string | null;         // A / B / O / AB / 不详 / 未查
  rhBloodType: string | null;       // 阴 / 阳 / 不详 / 未查

  // 医生（8）
  departmentDirector: string | null;  // 科主任
  chiefPhysician: string | null;      // 主(副主)任医生
  attendingPhysician: string | null;  // 主治医生
  residentPhysician: string | null;   // 住院医生
  responsibleNurse: string | null;    // 责任护士
  traineePhysician: string | null;    // 进修医生
  internPhysician: string | null;     // 实习医生
  coder: string | null;               // 编码员

  // 质控（4）
  recordQuality: string | null;       // 甲 / 乙 / 丙
  qcPhysician: string | null;
  qcNurse: string | null;
  qcDate: string | null;

  // Metadata
  status?: 'draft' | 'confirmed' | 'checked';
  sourceHospital?: string | null;
  sourcePdfPath?: string | null;
  extractionConfidence?: number | null;
}

export function emptyRecord(): MedicalRecord {
  return {
    recordNo: '',
    name: null,
    gender: null,
    birthDate: null,
    age: null,
    idCardMasked: null,
    nationality: null,
    ethnicity: null,
    maritalStatus: null,
    occupation: null,
    ageDays: null,
    newbornBirthWeight: null,
    newbornAdmissionWeight: null,
    idCardType: null,
    birthPlace: null,
    nativePlace: null,
    currentAddress: null,
    currentPhone: null,
    currentZip: null,
    registeredAddress: null,
    registeredZip: null,
    workplace: null,
    workPhone: null,
    workZip: null,
    contactName: null,
    contactRelation: null,
    contactAddress: null,
    contactPhone: null,
    admissionDate: null,
    dischargeDate: null,
    lengthOfStay: null,
    admissionDept: null,
    dischargeDept: null,
    admissionRoute: null,
    dischargeStatus: null,
    admissionWard: null,
    dischargeWard: null,
    specialtyDept: null,
    outpatientDiagnosis: null,
    outpatientDiagnosisCode: null,
    outpatientAdmissionCondition: null,
    confirmedAfterAdmissionDate: null,
    mainDiagnosisCode: null,
    mainDiagnosisName: null,
    mainDiagnosisIcdVer: null,
    mainAdmissionCondition: null,
    mainDischargeCondition: null,
    mainNote: null,
    otherDiagnosisCount: null,
    diagnoses: [],
    // V9 supplementary
    injuryPoisoningCause: null,
    injuryPoisoningCode: null,
    pathologicalDiagnosis: null,
    pathologicalDiagnosisCode: null,
    pathologyNumber: null,
    drugAllergy: null,
    allergyDrugs: null,
    autopsy: null,
    bloodType: null,
    rhBloodType: null,
    departmentDirector: null,
    chiefPhysician: null,
    attendingPhysician: null,
    residentPhysician: null,
    responsibleNurse: null,
    traineePhysician: null,
    internPhysician: null,
    coder: null,
    recordQuality: null,
    qcPhysician: null,
    qcNurse: null,
    qcDate: null,
    sourceHospital: null,
  };
}

interface Option<T = string> {
  value: T;
  label: string;
}

export const GENDER_OPTIONS: Option[] = [
  { value: '男', label: '男' },
  { value: '女', label: '女' },
  { value: '未知', label: '未知' },
];

export const ICD_VER_OPTIONS: Option[] = [
  { value: 'ICD-10', label: 'ICD-10 国临版 2.0' },
  { value: 'ICD-9-CM-3', label: 'ICD-9-CM-3 国临版 3.0' },
];

export const ADMISSION_ROUTE_OPTIONS: Option[] = [
  { value: '门诊', label: '门诊' },
  { value: '急诊', label: '急诊' },
  { value: '其他医疗机构转入', label: '其他医疗机构转入' },
  { value: '其他', label: '其他' },
];

export const DISCHARGE_STATUS_OPTIONS: Option[] = [
  { value: '医嘱离院', label: '医嘱离院' },
  { value: '医嘱转院', label: '医嘱转院' },
  { value: '医嘱转社区/乡镇', label: '医嘱转社区/乡镇' },
  { value: '非医嘱离院', label: '非医嘱离院' },
  { value: '死亡', label: '死亡' },
  { value: '其他', label: '其他' },
];

export const ID_CARD_TYPE_OPTIONS: Option[] = [
  { value: '居民身份证', label: '居民身份证' },
  { value: '护照', label: '护照' },
  { value: '军官证', label: '军官证' },
  { value: '港澳通行证', label: '港澳通行证' },
  { value: '台胞证', label: '台胞证' },
  { value: '其他', label: '其他' },
];

export const MARITAL_STATUS_OPTIONS: Option[] = [
  { value: '未婚', label: '未婚' },
  { value: '已婚', label: '已婚' },
  { value: '丧偶', label: '丧偶' },
  { value: '离婚', label: '离婚' },
  { value: '其他', label: '其他' },
];

// HQMS RC014 入院病况
export const ADMISSION_CONDITION_OPTIONS: Option[] = [
  { value: '有',         label: '1 有' },
  { value: '临床未确定', label: '2 临床未确定' },
  { value: '情况不明',   label: '3 情况不明' },
  { value: '无',         label: '4 无' },
];

// HQMS RC015 出院情况
export const DISCHARGE_CONDITION_OPTIONS: Option[] = [
  { value: '治愈', label: '治愈' },
  { value: '好转', label: '好转' },
  { value: '未愈', label: '未愈' },
  { value: '死亡', label: '死亡' },
  { value: '其他', label: '其他' },
];

export const CONTACT_RELATION_OPTIONS: Option[] = [
  { value: '配偶', label: '配偶' },
  { value: '父母', label: '父母' },
  { value: '子女', label: '子女' },
  { value: '兄弟姐妹', label: '兄弟姐妹' },
  { value: '朋友', label: '朋友' },
  { value: '其他', label: '其他' },
];

// V9 supplementary: 药物过敏（无/有）
export const DRUG_ALLERGY_OPTIONS: Option[] = [
  { value: '无', label: '1 无' },
  { value: '有', label: '2 有' },
];

// V9 supplementary: 死亡患者尸检（是/否）
export const AUTOPSY_OPTIONS: Option[] = [
  { value: '是', label: '1 是' },
  { value: '否', label: '2 否' },
];

// V9 supplementary: 血型 ABO
export const BLOOD_TYPE_OPTIONS: Option[] = [
  { value: 'A',    label: '1 A' },
  { value: 'B',    label: '2 B' },
  { value: 'O',    label: '3 O' },
  { value: 'AB',   label: '4 AB' },
  { value: '不详', label: '5 不详' },
  { value: '未查', label: '6 未查' },
];

// V9 supplementary: Rh 血型
export const RH_OPTIONS: Option[] = [
  { value: '阴',   label: '1 阴' },
  { value: '阳',   label: '2 阳' },
  { value: '不详', label: '3 不详' },
  { value: '未查', label: '4 未查' },
];

// V9 supplementary: 病案质量
export const RECORD_QUALITY_OPTIONS: Option[] = [
  { value: '甲', label: '1 甲' },
  { value: '乙', label: '2 乙' },
  { value: '丙', label: '3 丙' },
];

// 主表主要字段：用于"病案质控缺项检查"。仅包含 V8 及之前的核心首页字段；
// V9 引入的损伤中毒/病理扩展/过敏血型/医生/质控等字段为补充字段，不在此列表。
export const BUSINESS_FIELDS: (keyof MedicalRecord)[] = [
  'recordNo', 'name', 'gender', 'birthDate', 'age', 'idCardMasked',
  'nationality', 'ethnicity', 'maritalStatus', 'occupation',
  'ageDays', 'newbornBirthWeight', 'newbornAdmissionWeight', 'idCardType',
  'birthPlace', 'nativePlace',
  'currentAddress', 'currentPhone', 'currentZip',
  'registeredAddress', 'registeredZip',
  'workplace', 'workPhone', 'workZip',
  'contactName', 'contactRelation', 'contactAddress', 'contactPhone',
  'admissionDate', 'dischargeDate', 'lengthOfStay',
  'admissionDept', 'dischargeDept', 'admissionRoute', 'dischargeStatus',
  'admissionWard', 'dischargeWard', 'specialtyDept',
  'outpatientDiagnosis', 'outpatientDiagnosisCode',
  'outpatientAdmissionCondition', 'confirmedAfterAdmissionDate',
  'mainDiagnosisCode', 'mainDiagnosisName', 'mainDiagnosisIcdVer',
  'mainAdmissionCondition', 'mainDischargeCondition', 'mainNote',
  'otherDiagnosisCount',
];
