// Mirrors V1 + V6 + V7 `medical_record_main` columns + diagnosis subtable.
// camelCase per Spring Data JPA SnakeCaseStrategy default mapping.

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
  pathologicalDiagnosis: string | null;

  // V7 — Other diagnoses (subtable rows; main diagnosis NOT included here)
  diagnoses: Diagnosis[];

  // Operations (5)
  mainOperationCode: string | null;
  mainOperationName: string | null;
  operationDate: string | null;
  operator: string | null;
  anesthesiaMethod: string | null;

  // Cost (4)
  totalCost: number | null;
  drugCost: number | null;
  operationCost: number | null;
  medicalServiceCost: number | null;

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
    pathologicalDiagnosis: null,
    diagnoses: [],
    mainOperationCode: null,
    mainOperationName: null,
    operationDate: null,
    operator: null,
    anesthesiaMethod: null,
    totalCost: null,
    drugCost: null,
    operationCost: null,
    medicalServiceCost: null,
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

export const ANESTHESIA_OPTIONS: Option[] = [
  { value: '全身麻醉', label: '全身麻醉' },
  { value: '椎管内麻醉', label: '椎管内麻醉' },
  { value: '局部麻醉', label: '局部麻醉' },
  { value: '神经阻滞', label: '神经阻滞' },
  { value: '其他', label: '其他' },
  { value: '无', label: '无' },
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

// Business fields used for completeness progress (excludes id/audit/source).
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
  'otherDiagnosisCount', 'pathologicalDiagnosis',
  'mainOperationCode', 'mainOperationName', 'operationDate', 'operator',
  'anesthesiaMethod',
  'totalCost', 'drugCost', 'operationCost', 'medicalServiceCost',
];
