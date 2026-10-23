package com.xcwk.scalehub.ui.states

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Wave 0 状态页文案目录。句子来自 15-states-errors.html，不依赖 Compose 运行时。
 */
class ErrorStateCatalogTest {

    @Test
    fun prototype_ref的十五个view都在目录里() {
        assertThat(ErrorStateKind.entries.map { it.viewId }).containsExactly(
            "v-rec-edit",
            "v-export",
            "v-score",
            "v-anomaly",
            "v-multimetric",
            "v-pair-timeout",
            "v-report-stale",
            "v-sync-fail",
            "v-fb-fail",
            "v-connect-guide",
            "v-troubleshoot",
            "v-privacy-qa",
            "v-notif-landing",
            "v-fullscreen-load",
            "v-longpress",
        ).inOrder()
    }

    @Test
    fun 每个状态页都带上原型里的关键句() {
        val required = mapOf(
            ErrorStateKind.RecEdit to listOf("编辑记录", "测量数值不可修改", "备注（可选）", "晨起、空腹、排便后测量", "仅时间与备注可编辑", "保存修改"),
            ErrorStateKind.Export to listOf("导出结果", "健康数据", "已生成", "下载链接发至邮箱", "小时内有效", "上次导出失败", "邮箱发送超时", "重试", "下载文件"),
            ErrorStateKind.Score to listOf("身体评分", "本次评分", "良好区间", "需改善", "优秀", "评分如何得出", "体脂率参考区间", "肌肉量与水分率", "内脏脂肪等级", "评分为估算参考", "非医疗诊断"),
            ErrorStateKind.Anomaly to listOf("体重趋势", "数据异常已剔除", "超范围", "未计入趋势", "缺少数据", "该时段无记录", "曲线为估算衔接", "异常值与缺测以虚线", "空心点标注"),
            ErrorStateKind.MultiMetric to listOf("趋势对比", "体重", "体脂率", "天内体重与体脂率同步下降", "方向一致"),
            ErrorStateKind.PairTimeout to listOf("连接设备", "配对超时", "秒内未能建立连接", "请轻踩秤面唤醒后重试", "重新配对", "返回"),
            ErrorStateKind.ReportStale to listOf("报告解读", "你有", "条新测量记录", "本报告基于旧数据", "重新分析", "旧报告", "基于", "日数据", "天体重下降", "重新分析后将更新", "纳入最新测量", "更新趋势结论", "重新生成建议", "旧报告保留在历史中", "不会被覆盖", "用最新数据重新分析"),
            ErrorStateKind.SyncFail to listOf("数据同步", "条记录同步失败", "其余", "条已同步", "上传失败", "网络中断", "重试", "已同步", "失败记录已存本机", "联网自动重试", "立即重试全部"),
            ErrorStateKind.FeedbackFail to listOf("意见反馈", "反馈提交失败", "网络开小差了", "反馈未发送", "填写内容已保留", "重新提交", "复制反馈内容"),
            ErrorStateKind.ConnectGuide to listOf("连接指南", "确认手机蓝牙已开启", "轻踩体脂秤唤醒", "屏幕点亮即进入可连接状态", "手机靠近秤", "米内", "内点击", "搜索设备", "首次配对后", "轻踩唤醒即自动重连", "我试试"),
            ErrorStateKind.Troubleshoot to listOf("故障排查", "哪里出了问题", "选择情况", "逐步排查", "搜不到体脂秤", "测量数据不准", "屏幕不亮", "无法开机", "数据没有同步", "以上都不是", "联系客服"),
            ErrorStateKind.PrivacyQa to listOf("隐私与数据安全", "数据", "加密存储", "绝不出售或用于广告", "数据存在哪里", "谁能看到我的数据", "如何导出或删除", "家人会看到我的数据吗", "查看完整隐私政策"),
            ErrorStateKind.NotifLanding to listOf("该测量啦", "轻衡", "固定时间测量让趋势更准", "现在", "开始测量", "早上好", "林悦", "今天还没测量", "赤脚上秤记录一次吧", "上次测量"),
            ErrorStateKind.FullscreenLoad to listOf("正在加载你的健康数据", "首次登录需同步全部记录", "已加载", "完成"),
            ErrorStateKind.LongPress to listOf("长按", "复制", "展开", "长按弹出菜单", "体脂率", "是脂肪重量占体重的比例", "比单看体重更能反映身体成分", "分享", "全选", "长文展开", "收起", "林悦", "天体重下降", "体脂率下降", "个百分点", "趋势稳健", "单次波动不代表脂肪变化", "建议持续关注", "天平均值", "返回"),
        )
        assertThat(required.keys).containsExactlyElementsIn(ErrorStateKind.entries)
        required.forEach { (kind, phrases) ->
            val text = errorStatePlainText(kind)
            phrases.forEach { phrase ->
                assertWithMessage("${kind.viewId} 缺少「$phrase」").that(text).contains(phrase)
            }
        }
    }

    @Test
    fun 枢纽四个弹层沿用原型文案() {
        assertThat(boundaryPlainText(ErrorDialogKind.DeleteFail)).contains("删除失败")
        assertThat(boundaryPlainText(ErrorDialogKind.DeleteFail)).contains("该记录仍保留")
        assertThat(boundaryPlainText(ErrorDialogKind.DeviceBusy)).contains("设备正被其他手机使用")
        assertThat(boundaryPlainText(ErrorDialogKind.DeviceBusy)).contains("一次只能连接一台手机")
        assertThat(boundaryPlainText(ErrorDialogKind.ClearChat)).contains("删除这段对话")
        assertThat(boundaryPlainText(ErrorDialogKind.ClearChat)).contains("清空全部对话")
        assertThat(boundaryPlainText(ErrorDialogKind.ShareReport)).contains("分享身体报告")
        assertThat(boundaryPlainText(ErrorDialogKind.ShareReport)).contains("包含姓名与测量数据")
        assertThat(boundaryPlainText(ErrorDialogKind.ShareReport)).contains("复制链接")
    }
}
