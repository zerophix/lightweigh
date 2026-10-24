package com.xcwk.scalehub.feature.measure

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-078 测量结果分享与导出纯逻辑单测。
 *
 * 覆盖验收里**可自动判定**的两部分：
 * - 文案逐字对齐原型 `16-measure-result.html` 的 v-share / v-export / v-export-done
 * - 可判定逻辑：导出可用性（格式 × 范围 × 字段组合）、导出请求构造、三态转移、
 *   分享渠道标签与提示语
 *
 * 视觉布局不在此测（需真机/截图）；分享 SDK、文件与发信是宿主职责，不在此测。
 */
class ResultShareTest {

    private fun state(
        step: ResultShareStep = ResultShareStep.Share,
        format: ExportFormat = ExportFormat.Csv,
        range: ExportRange = ExportRange.Current,
        fields: List<ExportField> = ExportField.entries,
    ) = ResultShareUiState(step = step, format = format, range = range, fields = fields)

    // ---- 分享页文案（原型 v-share） ----

    @Test
    fun `分享页标题与数据安全警示逐字取自原型`() {
        assertThat(SHARE_TITLE).isEqualTo("分享报告")
        assertThat(SHARE_WARNING).isEqualTo("分享图片将包含你的姓名与测量数据，请确认后再分享。")
    }

    @Test
    fun `四个分享渠道文案与顺序与原型一致`() {
        assertThat(ShareChannel.entries.map { it.label })
            .containsExactly("微信", "朋友圈", "保存图片", "复制链接")
            .inOrder()
    }

    @Test
    fun `分享渠道提示语逐字取自原型 data-toast`() {
        assertThat(ShareChannel.entries.map { it.notice })
            .containsExactly("已分享到微信", "已分享到朋友圈", "已保存到相册", "已复制链接")
            .inOrder()
    }

    @Test
    fun `分享卡示例数据逐字取自原型`() {
        val card = ShareCardData.sample()
        assertThat(card.brand).isEqualTo("轻衡健康 · 身体成分报告")
        assertThat(card.headline).isEqualTo("林悦 · 10月21日")
        assertThat(card.weight).isEqualTo("62.4")
        assertThat(card.weightUnit).isEqualTo("kg")
        assertThat(card.delta).isEqualTo("↓0.3 较上次")
        assertThat(card.metrics.map { it.value }).containsExactly("86", "28.6%", "22.1", "42.3").inOrder()
        assertThat(card.metrics.map { it.caption }).containsExactly("评分", "体脂", "BMI", "肌肉kg").inOrder()
        assertThat(card.footer).isEqualTo("轻衡 · 让健康轻一点")
    }

    @Test
    fun `分享页分区标签与取消按钮逐字取自原型`() {
        assertThat(SHARE_TO_LABEL).isEqualTo("分享到")
        assertThat(CANCEL).isEqualTo("取消")
    }

    // ---- 导出页文案（原型 v-export） ----

    @Test
    fun `导出页标题与分区标签逐字取自原型`() {
        assertThat(EXPORT_TITLE).isEqualTo("导出报告")
        assertThat(EXPORT_FORMAT_LABEL).isEqualTo("导出格式")
        assertThat(EXPORT_RANGE_LABEL).isEqualTo("导出范围")
        assertThat(EXPORT_FIELDS_LABEL).isEqualTo("包含字段")
    }

    @Test
    fun `导出格式三选一与原型一致`() {
        assertThat(ExportFormat.entries.map { it.label })
            .containsExactly("CSV", "PDF", "图片")
            .inOrder()
    }

    @Test
    fun `导出范围三项标题与副标题逐字取自原型`() {
        assertThat(ExportRange.entries.map { it.title })
            .containsExactly("本次测量", "近 30 天", "全部记录")
            .inOrder()
        assertThat(ExportRange.entries.map { it.subtitle })
            .containsExactly("10月21日 07:30", "09/21 — 10/21 · 20 次", "128 次")
            .inOrder()
    }

    @Test
    fun `包含字段两行按原型 5 加 5 分组逐字对齐`() {
        assertThat(EXPORT_FIELD_LINES).containsExactly(
            "测量时间 · 体重 · 体脂率 · BMI · 肌肉量",
            "水分率 · 内脏脂肪 · 基础代谢 · 骨量 · 身体评分",
        ).inOrder()
    }

    @Test
    fun `加密链接提示与生成按钮逐字取自原型`() {
        assertThat(EXPORT_BANNER).isEqualTo("导出文件通过加密链接发送到邮箱，24 小时内有效。")
        assertThat(EXPORT_GENERATE).isEqualTo("生成导出文件")
    }

    // ---- 导出成功页文案（原型 v-export-done） ----

    @Test
    fun `导出成功页四段文案逐字取自原型`() {
        assertThat(EXPORT_DONE_TITLE).isEqualTo("导出成功")
        assertThat(EXPORT_DONE_LINE_1).isEqualTo("文件已生成，加密链接已发送至邮箱")
        assertThat(EXPORT_DONE_LINE_2).isEqualTo("24 小时内有效，请尽快下载。")
        assertThat(EXPORT_DONE_BACK).isEqualTo("返回报告")
        assertThat(EXPORT_DONE_RESEND).isEqualTo("重新发送到邮箱")
    }

    @Test
    fun `成功页示例邮箱沿用原型`() {
        assertThat(SAMPLE_EXPORT_EMAIL).isEqualTo("linyue@example.com")
        assertThat(ResultShareUiState().email).isEqualTo("linyue@example.com")
    }

    // ---- 导出可用性 ----

    @Test
    fun `CSV 与 PDF 支持全部十项字段`() {
        assertThat(ExportFormat.Csv.supportedFields).containsExactlyElementsIn(ExportField.entries)
        assertThat(ExportFormat.Pdf.supportedFields).containsExactlyElementsIn(ExportField.entries)
        assertThat(ExportFormat.Csv.supportedFields).hasSize(10)
    }

    @Test
    fun `图片格式只承载分享卡四项指标`() {
        assertThat(ExportFormat.Image.supportedFields)
            .containsExactly(ExportField.BodyScore, ExportField.BodyFatRate, ExportField.Bmi, ExportField.MuscleMass)
            .inOrder()
    }

    @Test
    fun `字段为空时不可导出`() {
        assertThat(exportAvailability(ExportFormat.Csv, ExportRange.All, emptyList()))
            .isEqualTo(ExportAvailability.NoFieldSelected)
    }

    @Test
    fun `图片格式带明细字段时判定字段不支持`() {
        val withWater = ExportFormat.Image.supportedFields + ExportField.WaterRate
        assertThat(exportAvailability(ExportFormat.Image, ExportRange.Current, withWater))
            .isEqualTo(ExportAvailability.FieldNotSupported)
    }

    @Test
    fun `图片格式只能导出本次测量`() {
        val imageFields = ExportFormat.Image.supportedFields
        assertThat(exportAvailability(ExportFormat.Image, ExportRange.Current, imageFields))
            .isEqualTo(ExportAvailability.Available)
        assertThat(exportAvailability(ExportFormat.Image, ExportRange.Last30Days, imageFields))
            .isEqualTo(ExportAvailability.RangeUnsupportedForImage)
        assertThat(exportAvailability(ExportFormat.Image, ExportRange.All, imageFields))
            .isEqualTo(ExportAvailability.RangeUnsupportedForImage)
    }

    @Test
    fun `CSV 与 PDF 支持跨记录范围`() {
        assertThat(exportAvailability(ExportFormat.Csv, ExportRange.All, ExportField.entries))
            .isEqualTo(ExportAvailability.Available)
        assertThat(exportAvailability(ExportFormat.Pdf, ExportRange.Last30Days, ExportField.entries))
            .isEqualTo(ExportAvailability.Available)
    }

    @Test
    fun `字段判定的优先级高于图片范围判定`() {
        // 既有字段问题又跨范围时，只报字段原因（宿主按单一原因提示）
        assertThat(exportAvailability(ExportFormat.Image, ExportRange.All, emptyList()))
            .isEqualTo(ExportAvailability.NoFieldSelected)
    }

    // ---- 导出请求构造 ----

    @Test
    fun `可用组合产出完整导出请求`() {
        val request = buildExportRequest(
            state(format = ExportFormat.Pdf, range = ExportRange.Last30Days),
        )
        assertThat(request).isNotNull()
        assertThat(request!!.format).isEqualTo(ExportFormat.Pdf)
        assertThat(request.range).isEqualTo(ExportRange.Last30Days)
        assertThat(request.fields).containsExactlyElementsIn(ExportField.entries)
        assertThat(request.email).isEqualTo("linyue@example.com")
    }

    @Test
    fun `不可用组合不产出导出请求`() {
        assertThat(buildExportRequest(state(format = ExportFormat.Image, range = ExportRange.All))).isNull()
        assertThat(buildExportRequest(state(fields = emptyList()))).isNull()
    }

    // ---- 三态转移 ----

    @Test
    fun `初始态为分享预览`() {
        assertThat(ResultShareUiState().step).isEqualTo(ResultShareStep.Share)
    }

    @Test
    fun `选中渠道只记录渠道不触发跳转`() {
        val next = resultShareReduce(state(), ResultShareIntent.SelectChannel(ShareChannel.CopyLink))
        assertThat(next.lastShareChannel).isEqualTo(ShareChannel.CopyLink)
        assertThat(next.step).isEqualTo(ResultShareStep.Share)
    }

    @Test
    fun `进入导出页只改步骤`() {
        val next = resultShareReduce(state(), ResultShareIntent.OpenExport)
        assertThat(next.step).isEqualTo(ResultShareStep.Export)
        assertThat(next.format).isEqualTo(ExportFormat.Csv)
        assertThat(next.range).isEqualTo(ExportRange.Current)
    }

    @Test
    fun `切换到图片格式时字段收敛到该格式支持范围`() {
        val next = resultShareReduce(state(), ResultShareIntent.SelectFormat(ExportFormat.Image))
        assertThat(next.format).isEqualTo(ExportFormat.Image)
        assertThat(next.fields).containsExactlyElementsIn(ExportFormat.Image.supportedFields)
    }

    @Test
    fun `图片格式跨范围时生成动作不进入成功页`() {
        // 防御第二道：即便宿主绕过置灰按钮直接发意图，也不能落到成功页
        val imageState = state(
            step = ResultShareStep.Export,
            format = ExportFormat.Image,
            range = ExportRange.All,
            fields = ExportFormat.Image.supportedFields,
        )
        val next = resultShareReduce(imageState, ResultShareIntent.GenerateExport)
        assertThat(next.step).isEqualTo(ResultShareStep.Export)
        assertThat(next.range).isEqualTo(ExportRange.All)
    }

    @Test
    fun `可用组合生成后进入导出成功页`() {
        val exportState = state(step = ResultShareStep.Export)
        val next = resultShareReduce(exportState, ResultShareIntent.GenerateExport)
        assertThat(next.step).isEqualTo(ResultShareStep.ExportDone)
    }

    @Test
    fun `选择范围只改范围不影响格式与字段`() {
        val next = resultShareReduce(state(), ResultShareIntent.SelectRange(ExportRange.Last30Days))
        assertThat(next.range).isEqualTo(ExportRange.Last30Days)
        assertThat(next.format).isEqualTo(ExportFormat.Csv)
        assertThat(next.fields).hasSize(10)
    }
}
