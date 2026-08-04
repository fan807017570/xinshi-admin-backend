# 班级成绩统计 — 领域建模设计文档

> 版本：v2.1 | 日期：2026-08-03 | 作者：Anran Fan

---

## 目录

1. [业务动机](#1-业务动机)
2. [通用语言](#2-通用语言ubiquitous-language)
3. [领域模型](#3-领域模型)
4. [聚合设计](#4-聚合设计)
5. [领域服务](#5-领域服务)
6. [应用流程](#6-应用流程)
7. [接口与展现](#7-接口与展现)
8. [文件变更与依赖](#8-文件变更与依赖)
9. [验证方案](#9-验证方案)

---

## 1. 业务动机

当前系统以**学生个体**为粒度管理成绩（`ScoresView.vue`），班主任可以逐一查看/录入每位学生的各科成绩和综合评价。但当需要从**班级维度**回答以下问题时，缺乏支撑：

> - 这个班语文平均分多少？数学呢？哪些科目整体偏弱？
> - 分数分布是正态还是两极分化？
> - 哪些学生总分不错但存在明显偏科，值得重点关注？

本需求引入一个新的**分析型限界上下文**——班级成绩统计，从班级维度聚合成绩数据并提供统计洞察。

---

## 2. 通用语言（Ubiquitous Language）

| 术语（中 / EN） | 定义 | 所属上下文 |
|---|---|---|
| **班级** Class | 一组学生与一位班主任的集合，有年级和届次属性 | 班级管理 |
| **学生** Student | 归属于某个班级的学习者，有学号和姓名 | 学生管理 |
| **科目** Subject | 一门可教授并可考试评分的课程，有满分范围 | 课程管理 |
| **班课** ClassSubject | 某学期某班级开设的具体科目实例，关联任课老师 | 课程管理 |
| **考试成绩** CourseResult | 某个学生在某次考试中某科目的得分与评语 | 成绩管理 |
| **考试类型** ExamType | 考试的类别（期中、期末、模拟考等） | 配置管理 |
| **学期** AcademicTerm | 学年中的一段教学周期 | 配置管理 |
| **科目统计** SubjectStatistics | 某班级在某考试中各科目的聚合统计指标 | **本次新增** |
| **分数段** ScoreBucket | 按 5 分间隔切分的成绩区间，含人数与占比 | **本次新增** |
| **班级总分** StudentTotalScore | 一个学生在某次考试中所有科目的得分加总 | **本次新增** |
| **班级成绩报告** ClassScoreReport | 一次查询的聚合根，包含统计总览、各科统计、分布图、偏科分析 | **本次新增** |
| **偏科记录** WeaknessItem | 总分高于班级平均分但单科低于该科平均分的学生+科目组合 | **本次新增** |
| **及格线** PassLine | = `maxScore × 0.6` | **本次新增** |

---

## 3. 领域模型

### 3.1 现有领域对象（只读复用）

```plantuml
@startuml 现有领域模型
skinparam backgroundColor #FEFEFE
skinparam classBorderColor #334155
skinparam classBackgroundColor #F8FAFC
skinparam arrowColor #64748B

package "成绩管理上下文 (已有)" #F1F5F9 {

  class SchoolClass {
    + id: long
    + className: string
    + gradeLevel: int
    + headTeacherUserId: long
    --
    班主任所管理的班级
  }

  class Student {
    + id: long
    + studentNo: string
    + studentName: string
    + classId: long
    --
    归属于某个班级
  }

  class Subject {
    + id: long
    + subjectCode: string
    + subjectName: string
    + minScore: double
    + maxScore: double
    --
    课程的满分范围 (ScoreRange)
  }

  class ClassSubject {
    + id: long
    + academicTermId: long
    + classId: long
    + subjectId: long
    + teacherUserId: long
    --
    某学期某班开设的科目实例
  }

  class CourseResult <<Entity>> {
    + id: long
    + academicTermId: long
    + classSubjectId: long
    + studentId: long
    + examTypeId: long
    + score: double
    + performanceComment: string
    + strengths: string
    + improvementPoints: string
    --
    考试成绩（本次统计数据源）
  }

  class ExamType {
    + id: long
    + examTypeName: string
    --
    考试类别：期中/期末/模拟
  }

  class AcademicTerm {
    + id: long
    + termName: string
    + academicYear: string
    --
    学期
  }
}

SchoolClass  "1" -- "*" Student : 包含 >
Student  "1" -- "*" CourseResult : 拥有 >
ClassSubject "1" -- "*" CourseResult : 关联 >
Subject "1" -- "*" ClassSubject : 定义 >
ExamType "1" -- "*" CourseResult : 分类 >
AcademicTerm "1" -- "*" ClassSubject : 所属学期 >

@enduml
```

### 3.2 新增领域对象（值对象 + 聚合根）

```plantuml
@startuml 新增领域模型
skinparam backgroundColor #FEFEFE
skinparam classBorderColor #0F766E
skinparam classBackgroundColor #ECFDF5
skinparam arrowColor #0F766E

package "班级成绩统计上下文 (新增)" #F0FDFA {

  class ClassScoreReport <<Aggregate Root>> {
    + classId: long
    + className: string
    + academicTermId: long
    + examTypeId: long
    + studentCount: int
    + subjectCount: int
    + totalAvg: double
    + totalMedian: double
    --
    聚合根，一次查询的完整报告
    Lifecycle: 请求内瞬态
  }

  class SubjectStatistics <<Value Object>> {
    + subjectId: long
    + subjectName: string
    + minScore: double
    + maxScore: double
    + avgScore: double
    + medianScore: double
    + maxResult: double
    + minResult: double
    + passLine: double
    + passRate: int
    + totalStudents: int
  }

  class ScoreBucket <<Value Object>> {
    + label: string
    + min: double
    + max: double
    + count: int
    + percent: int
    --
    5分间隔区间
    约束: percent总和≈100
  }

  class WeaknessItem <<Value Object>> {
    + studentId: long
    + studentName: string
    + totalScore: double
    + weakSubjectName: string
    + score: double
    + subjectAvg: double
    + gap: double
    --
    约束: totalScore > 班级总分平均
      AND score < 该科平均分
  }
}

ClassScoreReport "1" -- "*" SubjectStatistics : 包含 >
SubjectStatistics "1" -- "*" ScoreBucket : 分布 >
ClassScoreReport "1" -- "*" WeaknessItem : 分析 >

note right of ClassScoreReport
  <b>不变性约束:</b>
  I1: SubjectStatistics.totalStudents ≤ studentCount
  I2: Σ ScoreBucket.percent ≈ 100
  I3: passLine = maxScore × 0.6
  I4: WeaknessItem 双向条件过滤
  I5: subjectStats 数量 = subjectCount
  I6: 单一考试类型上下文
end note

@enduml
```

### 3.3 上下文映射

```plantuml
@startuml 限界上下文映射
skinparam backgroundColor #FEFEFE
skinparam rectangleBorderColor #64748B
skinparam rectangleBackgroundColor #F8FAFC
skinparam arrowColor #0F766E

rectangle "成绩管理上下文\n(已有)" as ScoreCtx #F1F5F9 {
  usecase "录入/查看成绩" as UC1
  usecase "班主任评价" as UC2
  usecase "Excel导入导出" as UC3
}

rectangle "班级成绩统计上下文\n(本次新增)" as StatsCtx #F0FDFA {
  usecase "班级维度聚合分析" as UC4
  usecase "分数分布可视化" as UC5
  usecase "偏科学生识别" as UC6
}

rectangle "外部依赖" as Ext #FEF3C7 {
  database "school_student_course_result" as DB
  interface "GET /api/student-results" as API
}

StatsCtx -down-> API : 只读查询
API -down-> DB : SQL
ScoreCtx -down-> DB : 读写

note top of Ext
  <b>集成方式:</b>
  统计上下文通过现有 REST API
  读取成绩数据，在前端完成聚合。
  不修改任何源数据。
end note

note right of StatsCtx
  <b>防腐层:</b>
  rows() / payload() 函数
  将后端 JSON 映射为
  前端 AnyRow 类型
end note

@enduml
```

---

## 4. 聚合设计

```plantuml
@startuml 聚合结构
skinparam backgroundColor #FEFEFE
skinparam packageBorderColor #0F766E
skinparam packageBackgroundColor #F0FDFA

package "ClassScoreReport Aggregate" {
  
  component ClassScoreReport as Root <<Aggregate Root>> #0F766E {
    () studentCount
    () subjectCount
    () totalAvg
    () totalMedian
  }

  package "SubjectStatistics × N" as SS #ECFDF5 {
    component "SubjectStatistics" as SubStat {
      () avgScore
      () medianScore
      () passRate
    }
    
    package "ScoreBucket × M" as SB #F0FDFA {
      component "ScoreBucket" as Bucket {
        () count
        () percent
      }
    }
  }

  package "WeaknessItem × K" as WI #FEF2F2 {
    component "WeaknessItem" as Weak {
      () totalScore
      () gap
    }
  }
}

Root --> SS : 1..N
SubStat --> Bucket : 1..M
Root --> WI : 0..K

note bottom of Root
  <b>聚合边界规则:</b>
  · 外部只能通过 Root 访问内部对象
  · ScoreBucket 不能脱离 SubjectStatistics 存在
  · 整个聚合生命周期 = 单次 HTTP 请求
  · 不持久化，前端计算后即渲染
end note

@enduml
```

---

## 5. 领域服务

### 5.1 ClassScoreReportBuilder（报告构建器）

```plantuml
@startuml 领域服务
skinparam backgroundColor #FEFEFE
skinparam componentBackgroundColor #F0FDFA
skinparam noteBackgroundColor #FFFBEB

component "ClassScoreReportBuilder" as Builder <<Domain Service>> #0F766E {
  
  () "build(rawResults)" as Entry

  folder "Step 1: 分组" as S1 {
    [groupBySubject]
  }

  folder "Step 2: 科目统计 × N" as S2 {
    [calcAvg]
    [calcMedian]
    [calcExtremes]
    [calcPassRate]
    [buildBuckets]
  }

  folder "Step 3: 学生总分" as S3 {
    [calcStudentTotalScores]
  }

  folder "Step 4-5: 班级总览" as S4 {
    [calcTotalAvg]
    [calcTotalMedian]
  }

  folder "Step 6: 偏科分析" as S6 {
    [findWeaknessItems]
  }

  folder "Step 7: 组装" as S7 {
    [assembleReport]
  }
}

Entry --> S1
S1 --> S2
S2 --> S3
S3 --> S4
S4 --> S6
S6 --> S7
S7 --> [ClassScoreReport]

note right of Builder
  <b>输入:</b> List<CourseResult>
  <b>输出:</b> ClassScoreReport 聚合根
  <b>位置:</b> 前端 ClassStatsView.vue 内
  <b>性质:</b> 纯函数，无副作用
end note

@enduml
```

### 5.2 核心算法规格

#### buildBuckets — 分数段构建

```
FUNCTION buildBuckets(scores[], minScore, maxScore, binSize = 5)
  binStart  = floor(minScore / binSize) * binSize
  binEnd    = ceil(maxScore / binSize) * binSize
  bins      = []

  FOR low = binStart; low < binEnd; low += binSize:
    high    = low + binSize
    isLast  = (high >= binEnd)
    count   = isLast
      ? COUNT(scores WHERE score ≥ low AND score ≤ high)
      : COUNT(scores WHERE score ≥ low AND score < high)

    bins.ADD(ScoreBucket{
      label:   "{low}-{high}",
      min:     low,   max:    high,
      count:   count,
      percent: ROUND(count / scores.length * 100)
    })
  RETURN bins
```

#### findWeaknessItems — 偏科识别

```
FUNCTION findWeaknessItems(rawResults[], subjectStats[], totalAvg)
  studentTotals = GROUP rawResults BY studentId
                    MAP (id, SUM of scores)

  topStudents = FILTER studentTotals WHERE totalScore > totalAvg

  items = []
  FOR EACH (studentId, totalScore) IN topStudents:
    FOR EACH score IN studentId.scores:
      stat = FIND subjectStats WHERE subjectId = score.subjectId
      IF stat == null: SKIP
      IF score < stat.avgScore:
        items.ADD(WeaknessItem{
          studentId, studentName,
          totalScore, weakSubjectName: stat.subjectName,
          score, subjectAvg: stat.avgScore,
          gap: stat.avgScore - score
        })

  SORT items BY gap DESC
  RETURN items
```

---

## 6. 应用流程

### 6.1 查询流程序列图

```plantuml
@startuml 查询流程序列图
skinparam backgroundColor #FEFEFE
skinparam participantBackgroundColor #F0FDFA
skinparam participantBorderColor #0F766E
skinparam arrowColor #334155

actor 班主任 as Teacher
participant "ClassStatsView\n(前端页面)" as View
participant "schoolApi\n(API 封装层)" as Api
participant "ClassScoreReportBuilder\n(领域服务)" as Builder
database "Backend\n/api/student-results" as Backend

Teacher -> View : 选择学期、班级、考试类型\n点击「查询」
activate View

View -> Api : listStudentResults({\n  academicTermId, classId, examTypeId\n})
activate Api

Api -> Backend : GET /api/student-results
activate Backend
Backend --> Api : List<CourseResult>
deactivate Backend

Api --> View : rawResults[]
deactivate Api

View -> Builder : build(rawResults)
activate Builder

Builder -> Builder : Step1: groupBySubject(rawResults)
Builder -> Builder : Step2: forEach subject\n  calcAvg, calcMedian\n  calcExtremes, calcPassRate\n  buildBuckets
Builder -> Builder : Step3: calcStudentTotalScores
Builder -> Builder : Step4-5: calcTotalAvg, calcTotalMedian
Builder -> Builder : Step6: findWeaknessItems
Builder -> Builder : Step7: assembleReport

Builder --> View : ClassScoreReport{...}
deactivate Builder

View -> View : 渲染统计卡片\n渲染科目统计表\n渲染柱状图\n渲染偏科列表

deactivate View

@enduml
```

### 6.2 页面状态机

```plantuml
@startuml 页面状态机
skinparam backgroundColor #FEFEFE
skinparam stateBackgroundColor #F0FDFA
skinparam stateBorderColor #0F766E
skinparam arrowColor #64748B

[*] --> idle : 页面加载\n(no query)

state idle : 请选择学期、班级和\n考试类型后查询

idle --> loading : 点击「查询」

state loading : 正在加载成绩数据...

loading --> error : API 请求失败
loading --> ready_empty : 返回空数组
loading --> ready : 有成绩数据

state ready_empty : 该班级暂无成绩数据\n请先录入成绩

state error : 加载失败\n(显示错误信息)

state ready {
  [*] --> showing

  state showing : 统计数据已加载

  showing : 统计总览卡片
  showing : 科目统计表
  showing : 柱状图
  showing : 偏科列表
}

ready      --> loading : 修改筛选条件重新查询
ready_empty --> loading : 修改筛选条件重新查询
error      --> loading : 重新查询

@enduml
```

---

## 7. 接口与展现

### 7.1 数据源 API

| API | 方法 | 说明 |
|-----|------|------|
| `/api/student-results` | GET | `academicTermId` + `classId` + `examTypeId`（均必填），返回 `List<Map>` |
| `/api/academic-terms` | GET | 学期列表 |
| `/api/classes` | GET | 班级列表（已做角色权限过滤） |
| `/api/exam-types` | GET | 考试类型列表 |

> ⚠️ `examTypeId` 在本功能中为**必选**，避免多考次数据混叠导致统计失真。

### 7.2 页面组件树

```plantuml
@startuml 页面组件树
skinparam backgroundColor #FEFEFE
skinparam componentBackgroundColor #F8FAFC
skinparam componentBorderColor #CBD5E1

component ClassStatsView {
  
  component "FilterPanel\n(筛选区)" as Filter {
    [学期下拉]
    [班级下拉]
    [考试类型下拉]
    [查询按钮]
  }

  component "OverviewCards\n(.metrics 4列)" as Cards {
    [班级人数]
    [科目数]
    [总分平均]
    [总分中位数]
  }

  component "SubjectStatsTable\n(科目统计表)" as Table {
    [科目|平均分|中位数|...]
  }

  component "ChartGrid\n(柱状图区)" as Charts {
    component "ChartCard × N\n(SVG 5分间隔柱状图)" as ChartCard {
      [<svg> 柱状图]
      [科目标题]
    }
  }

  component "WeaknessPanel\n(偏科分析)" as Weakness {
    [学生|总分|薄弱科目|...]
  }
}

Filter -down-> Cards : 查询结果驱动
Filter -down-> Table
Filter -down-> Charts
Filter -down-> Weakness

note right of Charts
  每个科目一个 ChartCard
  CSS Grid 2 列布局
  小屏响应式单列
end note

@enduml
```

### 7.3 菜单配置

| 配置项 | 值 |
|--------|-----|
| 路由路径 | `/class-stats` |
| menuKey | `classStats` |
| 菜单顺序 | 在 `scores` 之后，`transcripts` 之前 |
| 中文标签 | `班级成绩统计` |
| 图标 | 📈 |

### 7.4 权限与可见性

```plantuml
@startuml 权限可见性
skinparam backgroundColor #FEFEFE
skinparam activityBackgroundColor #F0FDFA
skinparam activityBorderColor #0F766E

start

:用户登录;
:GET /auth/me 返回 menus[];

if (menus 包含 "classStats"?) then (是)
  :侧边栏显示「📈 班级成绩统计」;
  :用户可以访问 /class-stats;
  
  :调用 GET /api/student-results;
  
  if (角色?) then (SUPER_ADMIN)
    :返回全部班级成绩;
  elseif (HEAD_TEACHER) then (是)
    :仅返回自己班级的成绩;
  elseif (TEACHER) then (是)
    :仅返回任教班级的成绩;
  else (PARENT)
    :仅返回子女的成绩;
  endif
  
  :页面展示统计结果;
  
else (否)
  :菜单不可见;
  :路由守卫拦截访问;
endif

stop

@enduml
```

---

## 8. 文件变更与依赖

### 8.1 变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `xinshi-admin-frontend/src/views/ClassStatsView.vue` | **新建** | ~500 行，包含领域服务逻辑 + SVG 图表 |
| `xinshi-admin-frontend/src/router/index.ts` | 修改 | +5 行，新增 `/class-stats` 路由 |
| `xinshi-admin-frontend/src/App.vue` | 修改 | +3 行，新增 menuOrder/staticLabels/menuIcons |
| 后端 menus 数据 | **需同步** | 将对应用户角色的 `menus` 加入 `classStats` |

### 8.2 依赖关系

```plantuml
@startuml 依赖关系
skinparam backgroundColor #FEFEFE
skinparam componentBackgroundColor #F8FAFC
skinparam componentBorderColor #64748B
skinparam arrowColor #0F766E

component "ClassStatsView.vue\n(新页面)" as New {
}

package "已有依赖 (不变)" #F1F5F9 {
  component "schoolApi\nlistStudentResults()" as Api
  component "schoolApi\nlistAcademicTerms()" as TermApi
  component "schoolApi\nlistClasses()" as ClassApi
  component "schoolApi\nlistExamTypes()" as ExamApi
  component "errorStore\n(全局错误弹窗)" as Err
  component "全局CSS\n.page .panel .metrics\n.card-form .table-wrap" as CSS
}

New -down-> Api : 获取成绩数据 >
New -down-> TermApi : 获取学期列表 >
New -down-> ClassApi : 获取班级列表 >
New -down-> ExamApi : 获取考试类型 >
New -down-> Err : 错误上报 >
New -down-> CSS : 样式复用 >

note right of New
  <b>零新依赖:</b>
  不引入任何 npm 包
  不新增后端接口
  不修改数据库
end note

@enduml
```

---

## 9. 验证方案

| 编号 | 验证项 | 方法 | 预期 |
|------|--------|------|------|
| V1 | 路由可达 | 访问 `/class-stats` | 页面正常渲染 |
| V2 | 聚合计算 | 准备已知数据集，人工计算各项统计 | 与页面结果一致 |
| V3 | 分数段完整性 | 检查 buckets 的 percent 总和 | ≈ 100（±1） |
| V4 | 偏科分析 | 人工筛选符合条件的学生 | 页面结果一致 |
| V5 | 不变性约束 | 逐一验证 I1–I6 | 全部满足 |
| V6 | 空状态 | 选择无成绩班级 | 显示空状态提示 |
| V7 | 权限安全 | 以不同角色登录验证 | API 返回范围受限 |
| V8 | 柱状图渲染 | 检查各科 bin 划分 | 正确，hover 信息完整 |
