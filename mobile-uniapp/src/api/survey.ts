import { get, post } from '@/utils/request';

/**
 * 问卷表单 API (移动端)。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)
 *
 * 移动端只关注填写能力, 复用同一后端接口 (/api/v1/surveys/*), source=MOBILE_UNIAPP 标识来源。
 */

export interface SurveyOption {
  code: string;
  label: string;
}

export interface SurSurvey {
  id: string;
  surveyNo: string;
  title: string;
  description?: string;
  status: string;
  category?: string;
  anonymous?: boolean;
  maxResponsesPerUser?: number;
  startTime?: string;
  endTime?: string;
  responseCount?: number;
}

export interface SurQuestion {
  id: string;
  surveyId: string;
  questionCode: string;
  questionType: string;
  title: string;
  description?: string;
  required?: boolean;
  sortNo?: number;
  optionsJson?: string;
  validationJson?: string;
  logicJson?: string;
  matrixJson?: string;
}

export interface SurResponse {
  id: string;
  surveyId: string;
  responseNo: string;
  status: string;
  respondentId?: string;
  source?: string;
  startedTime?: string;
  submittedTime?: string;
  durationMs?: number;
}

export interface SurveyDetailVO {
  survey: SurSurvey;
  questions: SurQuestion[];
}

export interface AnswerItem {
  questionId: string;
  questionCode: string;
  answerValue?: string;
  answerText?: string;
  selectedOptions?: string;
  ratingScore?: number;
  durationMs?: number;
}

export interface SubmitResponseRequest {
  surveyId: string;
  source?: string;
  answers: AnswerItem[];
  remark?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page?: number;
  size?: number;
}

/** 分页查询问卷 (移动端默认只看 COLLECTING 状态) */
export function pageSurveys(params: {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  title?: string;
  category?: string;
}) {
  return get<PageResult<SurSurvey>>('/surveys', params);
}

/** 问卷详情聚合 (问卷 + 题目列表) */
export function getSurveyDetail(id: string) {
  return get<SurveyDetailVO>(`/surveys/${id}/detail`);
}

/** 启动答卷 (source=MOBILE_UNIAPP) */
export function startResponse(surveyId: string, source = 'MOBILE_UNIAPP') {
  // 后端 @RequestParam, 拼接到 URL
  const qs = `?surveyId=${encodeURIComponent(surveyId)}&source=${encodeURIComponent(source)}`;
  return post<SurResponse>(`/surveys/responses/start${qs}`);
}

/** 提交答卷 (字段校验 + 条件显隐验证) */
export function submitResponse(data: SubmitResponseRequest) {
  return post<SurResponse>('/surveys/responses/submit', data);
}

/** 解析 optionsJson 字符串为数组 */
export function parseOptions(json?: string): SurveyOption[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    if (Array.isArray(arr)) return arr;
  } catch {
    // ignore
  }
  return [];
}

/** 解析 logicJson, 返回规则数组 */
export function parseLogicRules(json?: string): any[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    if (Array.isArray(arr)) return arr;
  } catch {
    // ignore
  }
  return [];
}
