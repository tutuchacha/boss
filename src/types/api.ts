/**
 * 聘聊 · API 类型定义
 * =====================================================================
 * 与 docs/api-contract.md / docs/api-endpoints.md 同步。
 * 框架无关：所有接口入参/出参类型在此集中定义，service 层、组件、Mock 共享。
 * =====================================================================
 */

// ============================================================
// 通用
// ============================================================

/** 统一响应包络 */
export interface ApiEnvelope<T = unknown> {
  code: number
  message: string
  data: T
  traceId: string
  /** 仅 422 字段校验失败时出现 */
  errors?: FieldError[]
}

/** 字段级错误 */
export interface FieldError {
  field: string
  message: string
}

/** 游标分页 */
export interface CursorPagination {
  nextCursor: string | null
  hasMore: boolean
  /** 可选，仅在需要时返回（如收藏列表），高频列表不返回以省 COUNT */
  total?: number
}

export interface PaginatedData<T> {
  list: T[]
  pagination: CursorPagination
}

/** 分页请求参数 */
export interface PageParams {
  cursor?: string | null
  limit?: number
  sort?: string
}

// ============================================================
// 鉴权 / 用户
// ============================================================

export enum RoleEnum {
  JOB_SEEKER = 'JOB_SEEKER',
  HR = 'HR',
  COMPANY_ADMIN = 'COMPANY_ADMIN',
  PLATFORM_ADMIN = 'PLATFORM_ADMIN',
}

export enum SeekingStatusEnum {
  ACTIVE = 'ACTIVE',
  PASSIVE = 'PASSIVE',
  NOT_LOOKING = 'NOT_LOOKING',
}

export interface User {
  id: string
  phone: string
  email?: string
  role: RoleEnum
  status: 'ACTIVE' | 'SUSPENDED' | 'DELETED'
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

export interface UserProfile {
  id: string
  userId: string
  name: string
  avatarUrl?: string
  gender?: 'male' | 'female' | 'other'
  birthDate?: string
  workYears?: number
  edu?: string
  expectCat?: string
  expectSub?: string
  expectCity?: string
  expectSalaryMin?: number
  expectSalaryMax?: number
  seekingStatus: SeekingStatusEnum
  defaultResumeId?: string
}

export interface HrProfile {
  id: string
  userId: string
  companyId: string
  name: string
  title: string
  avatarUrl?: string
  intro?: string
}

export interface Company {
  id: string
  name: string
  logoUrl?: string
  intro?: string
  industry?: string
  stage?: string
  size?: string
  website?: string
  verified: boolean
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING'
  customDomain?: string
  themeColor?: string
}

export interface CompanyMember {
  id: string
  userId: string
  companyId: string
  role: 'ADMIN' | 'HR'
  joinedAt: string
  user?: User
  hrProfile?: HrProfile
}

/** 企业认证提交（docs/api-endpoints.md §5.6） */
export interface CompanyVerifyInput {
  /** 企业全称（与营业执照一致） */
  companyName: string
  /** 统一社会信用代码 */
  licenseNo: string
  /** 联系人姓名 */
  contactName: string
  /** 联系电话 */
  contactPhone: string
}

export interface TenantConfig {
  companyId: string
  maxJobs: number
  maxHrAccounts: number
  maxMonthlyReach: number
  features: Record<string, boolean>
  expiresAt?: string
}

// ============================================================
// 简历
// ============================================================

export interface ResumeBasics {
  name: string
  phone: string
  email?: string
  city?: string
  avatarUrl?: string
  summary?: string
}

export interface ResumeEducation {
  school: string
  major: string
  degree: string
  startDate: string
  endDate: string
}

export interface ResumeExperience {
  company: string
  title: string
  startDate: string
  endDate: string // '至今' 用 'present'
  description?: string
}

export interface ResumeProject {
  name: string
  role?: string
  startDate: string
  endDate: string
  description?: string
  link?: string
}

export interface ResumeContent {
  basics: ResumeBasics
  educations: ResumeEducation[]
  experiences: ResumeExperience[]
  projects: ResumeProject[]
  skills: string[]
}

export interface Resume {
  id: string
  userId: string
  name: string
  content: ResumeContent
  attachmentUrl?: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

// ============================================================
// 职位
// ============================================================

export enum JobStatusEnum {
  OPEN = 'OPEN',
  CLOSED = 'CLOSED',
  DRAFT = 'DRAFT',
}

export type ExpOption = '应届' | '1-3年' | '3-5年' | '5-10年' | '经验不限'
export type EduOption = '大专' | '本科' | '硕士' | '学历不限'
export type SalaryRangeKey = '不限' | '10K以下' | '10-20K' | '20-40K' | '40K以上'
export type JobSortKey = 'recommend' | 'latest'

/** 列表/搜索用精简版 */
export interface JobSummary {
  id: string
  title: string
  salaryMin: number
  salaryMax: number
  salary: string // '20-30K' 展示用
  city: string
  area?: string
  exp: ExpOption | string
  edu: EduOption | string
  cat: string
  sub: string
  tags: string[]
  company: Pick<Company, 'id' | 'name' | 'stage' | 'size'>
  hr: Pick<HrProfile, 'id' | 'name' | 'title'>
  days: number // 距今天数（展示用）
  publishedAt: string
  viewCount: number
  applyCount: number
}

/** 详情页用完整版 */
export interface JobDetail extends JobSummary {
  companyId: string
  hrId: string
  responsibilities?: string
  requirements?: string
  welfare: string[]
  intro?: string
  similarJobs?: JobSummary[]
}

/** 发布/更新入参 */
export interface JobUpsertInput {
  title: string
  salaryMin: number
  salaryMax: number
  city: string
  area?: string
  exp: ExpOption | string
  edu: EduOption | string
  cat: string
  sub: string
  tags: string[]
  responsibilities?: string
  requirements?: string
  welfare: string[]
}

/** 列表查询参数 */
export interface JobListParams extends PageParams {
  q?: string
  city?: string
  cat?: string
  sub?: string
  exp?: ExpOption | string
  sal?: SalaryRangeKey
  sort?: JobSortKey
}

// ============================================================
// 分类 / 字典
// ============================================================

export interface CategoryNode {
  id: string
  name: string
  sort: number
  icon?: string
  children: CategoryNode[]
}

export interface City {
  id: string
  code: string
  name: string
  province?: string
  hot: boolean
  sort: number
}

export interface DictBundle {
  exps: string[]
  edus: string[]
  salaryRanges: SalaryRangeKey[]
  welfare: string[]
}

// ============================================================
// 消息
// ============================================================

export enum MessageTypeEnum {
  TEXT = 'TEXT',
  RESUME = 'RESUME',
  INTERVIEW_INVITE = 'INTERVIEW_INVITE',
  JOB_CARD = 'JOB_CARD',
  SYSTEM = 'SYSTEM',
}

export enum MessageFromEnum {
  USER = 'USER',
  HR = 'HR',
  SYSTEM = 'SYSTEM',
}

export interface Message {
  id: string
  conversationId: string
  fromType: MessageFromEnum
  fromUserId?: string
  fromHrId?: string
  type: MessageTypeEnum
  /** 文本消息为字符串，其他类型为 JSON 字符串 */
  content: string
  createdAt: string
  readByUser: boolean
  readByHr: boolean
}

export interface Conversation {
  id: string
  jobId: string
  userId: string
  hrId: string
  companyId: string
  lastMessageAt: string
  userUnreadCount: number
  hrUnreadCount: number
  closedByHr: boolean
  createdAt: string
}

/** 会话列表项（含对方信息 + 末条消息） */
export interface ConversationListItem {
  conversation: Conversation
  job: Pick<JobSummary, 'id' | 'title' | 'city' | 'area' | 'salary'>
  hr: Pick<HrProfile, 'id' | 'name' | 'title' | 'avatarUrl'>
  company: Pick<Company, 'id' | 'name'>
  lastMessage?: Message
}

export interface SendMessageInput {
  type: MessageTypeEnum
  content: string
  idempotencyKey?: string
}

export interface StartConversationInput {
  jobId: string
  firstMessage?: string
}

// ============================================================
// 投递 / 面试
// ============================================================

export enum ApplicationStatusEnum {
  SUBMITTED = 'SUBMITTED',
  VIEWED = 'VIEWED',
  INTERVIEW = 'INTERVIEW',
  OFFER = 'OFFER',
  REJECTED = 'REJECTED',
  WITHDRAWN = 'WITHDRAWN',
}

export interface Application {
  id: string
  userId: string
  jobId: string
  companyId: string
  resumeId: string
  status: ApplicationStatusEnum
  coverLetter?: string
  createdAt: string
  updatedAt: string
}

export interface ApplicationWithJob extends Application {
  job: JobSummary
}

export interface ApplicationDetail extends Application {
  job: JobDetail
  resume: Resume
  interviews: Interview[]
}

export interface ApplyInput {
  jobId: string
  resumeId: string
  coverLetter?: string
}

export enum InterviewFormatEnum {
  ONSITE = 'ONSITE',
  VIDEO = 'VIDEO',
  PHONE = 'PHONE',
}

export enum InterviewStatusEnum {
  PENDING = 'PENDING',
  ACCEPTED = 'ACCEPTED',
  DECLINED = 'DECLINED',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
}

export interface Interview {
  id: string
  applicationId: string
  companyId: string
  scheduledAt: string
  format: InterviewFormatEnum
  location?: string
  onlineUrl?: string
  note?: string
  status: InterviewStatusEnum
  createdAt: string
  updatedAt: string
}

export interface InterviewWithJob extends Interview {
  job: JobSummary
}

export interface InterviewWithCandidate extends Interview {
  user: Pick<User, 'id' | 'phone'>
  profile: Pick<UserProfile, 'name' | 'avatarUrl'>
}

export interface InviteInterviewInput {
  scheduledAt: string
  format: InterviewFormatEnum
  location?: string
  onlineUrl?: string
  note?: string
}

// ============================================================
// 人才筛选（HR：按技能需求匹配候选人；演示版为关键词匹配）
// ============================================================

/** 筛选入参（§4.5）：query 为自然语言需求描述，与 tags 至少一个非空 */
export interface CandidateMatchInput {
  query?: string
  tags?: string[]
}

/** 命中证据（关联数据片段）：关键词命中的简历原文与所在栏目 */
export interface CandidateMatchEvidence {
  /** 命中的关键词（展示词典原文大小写） */
  token: string
  /** 所在栏目，如「技能 / 个人简介 / 工作经历 · 某公司 / 项目经历 · 某项目」 */
  field: string
  /** 命中片段（已带上下文省略号，前端按 token 高亮） */
  snippet: string
}

/** 单个候选人的匹配结果（§4.5 出参，按匹配度倒序） */
export interface CandidateMatchView {
  /** 用于跳转候选人详情（同一候选人多条投递时取最近一条） */
  applicationId: string
  candidate: {
    userId: string
    name: string
    expect: string
    city: string
    workYears: number
    edu: string
    resumeCompleteness: number
  }
  /** 应聘职位（最近一条投递） */
  jobTitle: string
  /** 匹配度分值（演示版为加权命中分，仅用于排序与展示） */
  score: number
  /** 命中的关键词（去重，按权重倒序） */
  hitTokens: string[]
  /** 关联证据片段（简历原文，前端高亮命中词） */
  evidences: CandidateMatchEvidence[]
}

// ============================================================
// 收藏
// ============================================================

export interface Favorite {
  id: string
  userId: string
  jobId: string
  createdAt: string
}

export interface FavoriteWithJob extends Favorite {
  job: JobSummary
}

// ============================================================
// 文件上传
// ============================================================

export type UploadType = 'avatar' | 'logo' | 'resume' | 'company'

export interface UploadSignInput {
  type: UploadType
  contentType: string
  ext: string
}

export interface UploadSignResult {
  uploadUrl: string
  objectKey: string
  expiresAt: string
}

export interface UploadConfirmInput {
  objectKey: string
}

export interface UploadConfirmResult {
  url: string
  contentType: string
  size: number
}

// ============================================================
// 鉴权接口入参 / 出参
// ============================================================

export interface SmsCodeInput {
  phone: string
  scene: 'LOGIN' | 'REGISTER' | 'RESET'
}

/** 发送验证码出参（§1.1） */
export interface SmsCodeResult {
  sentAt: string
  /** 距可再次发送的秒数（60s 限流） */
  nextRetryIn: number
}

export interface RegisterInput {
  phone: string
  code: string
  password: string
  role: RoleEnum.JOB_SEEKER | RoleEnum.HR
}

export interface LoginSmsInput {
  phone: string
  code: string
}

export interface LoginPasswordInput {
  account: string
  password: string
}

export interface AuthResult {
  accessToken: string
  refreshToken: string
  user: User
  profile?: UserProfile | HrProfile
}

export interface RefreshInput {
  refreshToken: string
}

export interface RefreshResult {
  accessToken: string
  refreshToken: string
}

// ============================================================
// 用户偏好（外观等）
// ============================================================

export type ThemePref = 'auto' | 'light' | 'dark'

export interface UserPreferences {
  theme: ThemePref
}

// ============================================================
// WebSocket 事件
// ============================================================

export type WsEvent =
  | { kind: 'message'; data: Message }
  | { kind: 'notification'; data: { id: string; type: string; title: string; body: string; createdAt: string } }
  | { kind: 'typing'; data: { conversationId: string; userId?: string; hrId?: string; isTyping: boolean } }
  | { kind: 'presence'; data: { conversationId: string; userId?: string; hrId?: string; online: boolean } }
  | { kind: 'read'; data: { conversationId: string; messageId: string; readBy: 'USER' | 'HR' } }
