package gov.anzong.androidnga.activity.compose.filter

import org.junit.Test


class FilterWordModelTest {


    @Test
    fun testConvertEntity() {
        // 无数据
        var testData = "{\"data\":{\"0\":\"\",\"1\":80,\"2\":0},\"time\":1758261069}"
        var result = FilterWordModel.convertEntity(testData)
        println("无数据测试结果：$result")
        assert(result.isSuccess)

        // 只有屏蔽用户
        testData = "{\"data\":{\"0\":\"1\\n\\n123456/用户1 654321/用户2\",\"1\":80,\"2\":4},\"time\":1758274653}"
        result = FilterWordModel.convertEntity(testData)
        println("只有屏蔽用户结果：$result")
        assert(result.isSuccess)

        // 只有屏蔽词
        testData = "{\"data\":{\"0\":\"1\\n关键词1 关键词2\\n\",\"1\":80,\"2\":5},\"time\":1758275100}"
        result = FilterWordModel.convertEntity(testData)
        println("只有屏蔽词结果：$result")
        assert(result.isSuccess)

        // 屏蔽用户 + 屏蔽词
        testData = "{\"data\":{\"0\":\"1\\n关键词1 关键词2\\n123456/用户1 654321/用户2\",\"1\":80,\"2\":3},\"time\":1758274201}"
        result = FilterWordModel.convertEntity(testData)
        println("屏蔽用户+屏蔽词结果：$result")
        assert(result.isSuccess)
    }
}