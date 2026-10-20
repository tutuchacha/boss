# verify-seed.ps1 — T1 种子抽查（各表行数 + 关键断言，对齐 docs/step3-plan.md §3 数字）
# 用法：先 dev-up.ps1 起库、后端启动完成（Flyway 迁移 V1~V9），再运行本脚本。
# 期望值：companies 16 · jobs 51（可见 38 / 云杉 7）· categories 84 · cities 9 · users 8
#         resumes 7 · HR 投递 6 · 求职者投递 3 · interviews 3 · HR 会话 6（未读合计 4）
#         求职者会话 2 · messages 16 · flyway 最新版本 9
# 注：冒烟产生的记录不计入 seed 行数：注册（T2b）u-reg-* / co-reg-* / hr-reg-* / cm-reg-*（users / companies），
#     职位写（T3b）job-new-%（jobs；软删只置 deleted_at，行保留）。
. "$PSScriptRoot\_env.ps1"

$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "未找到 mysql.exe：$mysql" }
if (-not (Test-TcpPort '127.0.0.1' $MySqlPort)) { throw "MySQL 未启动（127.0.0.1:$MySqlPort），先跑 dev-up.ps1" }

$sql = @'
SELECT 'flyway_version        ' AS item, MAX(version) AS actual, '9' AS expected FROM flyway_schema_history
UNION ALL SELECT 'companies             ', COUNT(*), '16' FROM companies WHERE id NOT LIKE 'co-reg-%'
UNION ALL SELECT 'jobs_total            ', COUNT(*), '51' FROM jobs WHERE id NOT LIKE 'job-new-%'
UNION ALL SELECT 'jobs_visible(OPEN)    ', COUNT(*), '38' FROM jobs WHERE status = 'OPEN' AND deleted_at IS NULL
UNION ALL SELECT 'jobs_yunshan          ', COUNT(*), '7' FROM jobs WHERE company_id = 'co-acgiw7' AND deleted_at IS NULL
UNION ALL SELECT 'categories            ', COUNT(*), '84' FROM categories
UNION ALL SELECT 'cities                ', COUNT(*), '9' FROM cities
UNION ALL SELECT 'users                 ', COUNT(*), '8' FROM users WHERE id NOT LIKE 'u-reg-%'
UNION ALL SELECT 'resumes               ', COUNT(*), '7' FROM resumes WHERE user_id NOT LIKE 'u-reg-%' AND deleted_at IS NULL
UNION ALL SELECT 'applications_hr       ', COUNT(*), '6' FROM applications WHERE company_id = 'co-acgiw7' AND id NOT LIKE 'app-new-%'
UNION ALL SELECT 'applications_seeker   ', COUNT(*), '3' FROM applications WHERE user_id = 'u-seeker-1' AND id NOT LIKE 'app-new-%'
UNION ALL SELECT 'interviews            ', COUNT(*), '3' FROM interviews WHERE id NOT LIKE 'int-new-%'
UNION ALL SELECT 'conversations_hr      ', COUNT(*), '6' FROM conversations WHERE company_id = 'co-acgiw7' AND id NOT LIKE 'conv-new-%'
UNION ALL SELECT 'conversations_seeker  ', COUNT(*), '2' FROM conversations WHERE user_id = 'u-seeker-1' AND id NOT LIKE 'conv-new-%'
UNION ALL SELECT 'hr_unread_total       ', COALESCE(SUM(hr_unread_count), 0), '4' FROM conversations WHERE company_id = 'co-acgiw7' AND id NOT LIKE 'conv-new-%'
UNION ALL SELECT 'messages              ', COUNT(*), '16' FROM messages WHERE id NOT LIKE 'msg-new-%';
'@

& $mysql -h 127.0.0.1 -P $MySqlPort -u root "-p$MySqlRootPwd" --default-character-set=utf8mb4 -t pinliao -e $sql
if ($LASTEXITCODE -ne 0) { throw "抽查 SQL 执行失败" }
