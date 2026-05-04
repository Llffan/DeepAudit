// Mirrors V1__init_schema.sql `medical_record_main` columns.
// camelCase per Spring Data JPA SnakeCaseStrategy default mapping.

export interface MedicalRecord {
  id?: number;

  // Identity (6)
  recordNo: string;
  name: string | null;
  gender: string | null;
  birthDate: string | null;
  age: number | null;
  idCardMasked: string | null;

  // Admission / discharge (7)
  admissionDate: string | null;
  dischargeDate: string | null;
  lengthOfStay: number | null;
  admissionDept: string | null;
  dischargeDept: string | null;
  admissionRoute: string | null;
  dischargeStatus: string | null;

  // Diagnoses (5)
  mainDiagnosisCode: string | null;
  mainDiagnosisName: string | null;
  mainDiagnosisIcdVer: string | null;
  otherDiagnosisCount: number | null;
  pathologicalDiagnosis: string | null;

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
    admissionDate: null,
    dischargeDate: null,
    lengthOfStay: null,
    admissionDept: null,
    dischargeDept: null,
    admissionRoute: null,
    dischargeStatus: null,
    mainDiagnosisCode: null,
    mainDiagnosisName: null,
    mainDiagnosisIcdVer: null,
    otherDiagnosisCount: null,
    pathologicalDiagnosis: null,
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
  { value: 'ICD-9-CM-3', label: 'ICD-9-CM-3 国临版 4.0' },
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

// Business fields used for completeness progress (excludes id/audit/source).
export const BUSINESS_FIELDS: (keyof MedicalRecord)[] = [
  'recordNo', 'name', 'gender', 'birthDate', 'age', 'idCardMasked',
  'admissionDate', 'dischargeDate', 'lengthOfStay', 'admissionDept',
  'dischargeDept', 'admissionRoute', 'dischargeStatus',
  'mainDiagnosisCode', 'mainDiagnosisName', 'mainDiagnosisIcdVer',
  'otherDiagnosisCount', 'pathologicalDiagnosis',
  'mainOperationCode', 'mainOperationName', 'operationDate', 'operator',
  'anesthesiaMethod',
  'totalCost', 'drugCost', 'operationCost', 'medicalServiceCost',
];
