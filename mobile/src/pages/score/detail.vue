<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { downloadScoreReport } from '@/api/pdf'
import { listStudentExamScores } from '@/api/score'
import { getPersonalStat } from '@/api/stat'
import { useCurrentStudent } from '@/composables/useCurrentStudent'
import type { PersonalStatResponse, ScoreResponse } from '@/types'
import { fmtDate, fmtPercent, fmtRank, fmtScore } from '@/utils/format'
import { errorMessage, toTrend, toast } from '@/utils/navigate'

interface SubjectRow {
  subjectId: number
  subjectName: string
  score?: number
  fullScore?: number
  rankInClass?: number
  rankInGrade?: number
  absent: boolean
  classAverage?: number
}

const { studentId, emptyHint } = useCurrentStudent()

const examId = ref<number | null>(null)
const stat = ref<PersonalStatResponse | null>(null)
const scores = ref<ScoreResponse[]>([])
const loading = ref(false)
const downloading = ref(false)
const error = ref('')

const parseExamId = (raw: unknown): number | null => {
  const n = Number(raw)
  return Number.isInteger(n) && n > 0 ? n : null
}

const fromStat = (s: PersonalStatResponse): SubjectRow[] =>
  (s.subjectScores ?? []).map((x) => ({
    subjectId: x.subjectId,
    subjectName: x.subjectName ?? `科目 #${x.subjectId}`,
    score: x.score,
    fullScore: x.fullScore,
    rankInClass: x.rankInClass,
    rankInGrade: x.rankInGrade,
    absent: x.isAbsent === true,
    classAverage: x.classAverage
  }))

const fromScores = (list: ScoreResponse[]): SubjectRow[] =>
  list.map((x) => ({
    subjectId: x.subjectId,
    subjectName: x.subjectName ?? `科目 #${x.subjectId}`,
    score: x.score,
    fullScore: x.fullScore,
    rankInClass: x.rankInClass,
    rankInGrade: x.rankInGrade,
    absent: x.isAbsent === 1
  }))

// ponytail: /stats/personal 是主数据源（含满分、总分名次、缺考）；它在"未找到学生成绩"时抛业务异常，
// 这时退回 /scores 列表仍能把各科分数呈现出来
const rows = computed<SubjectRow[]>(() => (stat.value ? fromStat(stat.value) : fromScores(scores.value)))
const hasClassAverage = computed(() => rows.value.some((r) => r.classAverage !== undefined))

const examName = computed(() => stat.value?.examName ?? scores.value[0]?.examName ?? '')
const className = computed(() => scores.value[0]?.className ?? '')
const examDate = computed(() => scores.value[0]?.recordedAt)

const totalScore = computed(() => {
  if (stat.value?.totalScore !== undefined) return stat.value.totalScore
  const valid = scores.value.filter((s) => s.isAbsent !== 1 && typeof s.score === 'number')
  return valid.length ? valid.reduce((sum, s) => sum + (s.score ?? 0), 0) : undefined
})
const totalFullScore = computed(() => {
  if (stat.value?.totalFullScore !== undefined) return stat.value.totalFullScore
  const fulls = scores.value.map((s) => s.fullScore).filter((v): v is number => typeof v === 'number')
  return fulls.length ? fulls.reduce((a, b) => a + b, 0) : undefined
})
const rankInClass = computed(() => stat.value?.rankInClass ?? scores.value[0]?.totalRankInClass)
const rankInGrade = computed(() => stat.value?.rankInGrade ?? scores.value[0]?.totalRankInGrade)

const load = async (): Promise<void> => {
  const sid = studentId.value
  const eid = examId.value
  if (sid === null || eid === null) return
  loading.value = true
  error.value = ''
  const [statRes, scoreRes] = await Promise.allSettled([getPersonalStat(eid, sid), listStudentExamScores(eid, sid)])
  stat.value = statRes.status === 'fulfilled' ? statRes.value : null
  scores.value = scoreRes.status === 'fulfilled' ? scoreRes.value.records : []
  if (statRes.status === 'rejected' && scoreRes.status === 'rejected') {
    error.value = errorMessage(statRes.reason, '加载失败')
  }
  loading.value = false
}

const download = async (): Promise<void> => {
  const sid = studentId.value
  const eid = examId.value
  if (sid === null || eid === null || downloading.value) return
  downloading.value = true
  try {
    await downloadScoreReport(eid, sid)
    toast('成绩单已生成', 'success')
  } catch (err) {
    toast(errorMessage(err, '下载失败'))
  } finally {
    downloading.value = false
  }
}

onLoad((query) => {
  examId.value = parseExamId(query?.examId)
  if (examId.value === null) {
    error.value = '缺少考试参数'
    return
  }
  void load()
})
</script>

<template>
  <view class="page">
    <EmptyState v-if="emptyHint" :text="emptyHint" />
    <view v-else-if="loading" class="loading">加载中…</view>
    <EmptyState v-else-if="error" :text="error" />
    <EmptyState v-else-if="rows.length === 0" text="该考试暂无成绩" />

    <template v-else>
      <view class="card summary">
        <view class="exam-name">{{ examName }}</view>
        <view class="muted">
          <text v-if="className">{{ className }}</text>
          <text v-if="examDate"> · 录入于 {{ fmtDate(examDate) }}</text>
        </view>
        <view class="total-row">
          <view class="total">
            <text class="total-value">{{ fmtScore(totalScore) }}</text>
            <text class="total-full">/ {{ fmtScore(totalFullScore) }}</text>
          </view>
          <view class="ranks">
            <view class="rank-item">
              <text class="rank-value">{{ fmtRank(rankInClass) }}</text>
              <text class="label">班级总分排名</text>
            </view>
            <view class="rank-item">
              <text class="rank-value">{{ fmtRank(rankInGrade) }}</text>
              <text class="label">年级总分排名</text>
            </view>
          </view>
        </view>
        <view v-if="stat?.averageScore !== undefined" class="muted avg">各科平均 {{ fmtScore(stat?.averageScore) }} 分</view>
      </view>

      <view class="card">
        <view class="card-title">各科成绩</view>
        <view class="table-head">
          <text class="col col-subject">科目</text>
          <text class="col col-score">得分</text>
          <text v-if="hasClassAverage" class="col col-avg">班均</text>
          <text class="col col-rank">班名</text>
          <text class="col col-rank">级名</text>
        </view>
        <view v-for="row in rows" :key="row.subjectId" class="table-row">
          <view class="col col-subject">
            <text>{{ row.subjectName }}</text>
            <text v-if="row.absent" class="tag tag-orange absent">缺考</text>
          </view>
          <view class="col col-score">
            <text class="score">{{ row.absent ? '—' : fmtScore(row.score) }}</text>
            <text class="muted small">/{{ fmtScore(row.fullScore) }} · {{ row.absent ? '—' : fmtPercent(row.score, row.fullScore) }}</text>
          </view>
          <text v-if="hasClassAverage" class="col col-avg">{{ fmtScore(row.classAverage) }}</text>
          <text class="col col-rank">{{ row.rankInClass ?? '—' }}</text>
          <text class="col col-rank">{{ row.rankInGrade ?? '—' }}</text>
        </view>
      </view>

      <view class="actions">
        <button class="btn btn-plain" @tap="toTrend">查看趋势</button>
        <button class="btn" :loading="downloading" :disabled="downloading" @tap="download">下载成绩单 PDF</button>
      </view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.summary {
  background: linear-gradient(135deg, #2f6fed, #5b8dff);
  color: #fff;

  .muted,
  .label {
    color: rgba(255, 255, 255, 0.85);
  }
}

.exam-name {
  font-size: 34rpx;
  font-weight: 600;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 28rpx;
}

.total {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.total-value {
  font-size: 76rpx;
  font-weight: 700;
  line-height: 1;
}

.total-full {
  font-size: 28rpx;
  opacity: 0.85;
}

.ranks {
  display: flex;
  gap: 32rpx;
}

.rank-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.rank-value {
  font-size: 30rpx;
  font-weight: 600;
}

.label {
  font-size: 22rpx;
  margin-top: 6rpx;
}

.avg {
  margin-top: 16rpx;
  font-size: 24rpx;
}

.table-head,
.table-row {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f0f1f3;
}

.table-head {
  color: #8a8f99;
  font-size: 24rpx;
}

.table-row:last-child {
  border-bottom: none;
}

.col {
  text-align: right;
}

.col-subject {
  flex: 1.4;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.col-score {
  flex: 1.6;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.col-avg {
  flex: 0.9;
}

.col-rank {
  flex: 0.8;
}

.score {
  font-size: 32rpx;
  font-weight: 600;
}

.small {
  font-size: 22rpx;
}

.absent {
  font-size: 20rpx;
}

.actions {
  display: flex;
  gap: 20rpx;

  .btn {
    flex: 1;
  }
}
</style>
