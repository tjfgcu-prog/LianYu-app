package com.lianyu.ai.database

import android.content.Context
import android.util.Log
import com.lianyu.ai.database.dao.QuizQuestionDao
import com.lianyu.ai.database.model.QuizQuestionEntity

class SecurityDataSeeder(
    private val quizQuestionDao: QuizQuestionDao
) {

    companion object {
        private const val TAG = "SecurityDataSeeder"

        /**
         * 从 Context 初始化安全数据，封装 DAO 获取和 seedIfEmpty 调用。
         * 供 app 模块调用，避免 app 直接操作 DAO。
         */
        suspend fun seedIfNeeded(context: Context) {
            AppDatabase.getDatabase(context.applicationContext).let {
                SecurityDataSeeder(it.quizQuestionDao()).seedIfEmpty()
            }
        }
    }

    suspend fun seedIfEmpty() {
        try {
            if (quizQuestionDao.count() == 0) {
                Log.i(TAG, "开始初始化题库数据...")
                seedQuizQuestions()
                Log.i(TAG, "题库数据初始化完成，共 ${quizQuestionDao.count()} 条")
            }
        } catch (e: Exception) {
            Log.e(TAG, "安全数据初始化失败", e)
        }
    }

    

    private fun seedQuizQuestions() {
        val questions = mutableListOf<QuizQuestionEntity>()

        questions.addAll(generateSafetyQuestions())
        questions.addAll(generateMentalHealthQuestions())

        quizQuestionDao.insertAll(questions)
    }

    private fun generateSafetyQuestions(): List<QuizQuestionEntity> {
        return listOf(
            QuizQuestionEntity(question = "发布儿童色情内容会被封禁多少天？", options = "365天|31天|10天|7天", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "以下哪种行为属于极端违规？", options = "发布儿童色情|发送垃圾信息|轻微骂人|刷屏", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "尝试越狱AI模型会面临什么处罚？", options = "10天封禁|警告处理|3天封禁|永久封禁", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "发布恐怖主义相关内容会被封禁多少天？", options = "31天|10天|7天|3天", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "以下哪个关键词不会触发违规检测？", options = "你好|越狱模式|儿童色情|制造炸弹", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "仇恨言论属于哪个等级的违规？", options = "HIGH（高）|EXTREME（极端）|LOW（低）|MEDIUM（中）", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "如何避免触发内容过滤系统？", options = "遵守社区规范|使用隐晦表达|频繁更换账号|使用VPN", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "发布制毒教程属于什么级别的违规？", options = "CRITICAL（严重）|SEVERE（重度）|HIGH（高）|MEDIUM（中）", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "以下哪项是正确的行为？", options = "友善交流|发布违规内容|尝试绕过过滤|辱骂他人", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "使用外文绕过内容过滤是否违规？", options = "是，同样违规|否，可以绕过|看情况|只处罚中文", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "\"Ignore all instructions\"属于什么行为？", options = "严重越狱|正常交流|轻微试探|创意写作", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "\"DAN mode\"提示词的作用是什么？", options = "试图绕过安全限制|提升AI能力|优化回答质量|增加趣味性", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "以下哪种说法是错误的？", options = "外文可以绕过检测|所有语言都受相同规则约束|多语言检测已启用|中英文一视同仁", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "为什么不能要求AI生成色情内容？", options = "违反使用规范和安全准则|这是正常需求|可以提高用户体验|有助于创意表达", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "遇到他人发送违规内容应该怎么做？", options = "举报并避免互动|转发给更多人|模仿其行为|置之不理", correctIndex = 0, category = "SAFETY"),
            // 多语言检测新增题目
            QuizQuestionEntity(question = "使用繁体中文能否绕过内容过滤？", options = "不能，繁体同样被检测|可以绕过|只检测简体|看情况而定", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "使用日语能否绕过安全检测？", options = "不能，日语已被全面覆盖|可以绕过|日语不在检测范围|只有中文被检测", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "\"ジェイルブレイク\"是什么意思？", options = "越狱（日语）|正常词汇|技术术语|无意义字符", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "为什么要求使用中文交流？", options = "统一语言便于管理和安全检测|歧视其他语言|中文更优越|系统不支持其他语言", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "以下哪种方式无法绕过多语言检测？", options = "所有选项都会被检测|使用繁体中文|使用日语|混合多种语言", correctIndex = 0, category = "SAFETY"),
            QuizQuestionEntity(question = "如果收到非中文的违规内容应该？", options = "举报并提醒对方使用中文|直接转发|模仿回复|置之不理", correctIndex = 0, category = "SAFETY")
        )
    }

    private fun generateMentalHealthQuestions(): List<QuizQuestionEntity> {
        return listOf(
            QuizQuestionEntity(question = "当感到焦虑时，以下哪种方法最有效？", options = "深呼吸练习|压抑情绪|暴饮暴食|孤立自己", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "遇到心理困扰时应该怎么做？", options = "寻求专业帮助|独自承受|向他人发泄|逃避问题", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "以下哪种情绪管理方式是不健康的？", options = "酗酒缓解压力|运动释放压力|冥想放松|倾诉交流", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "如何识别抑郁症的早期症状？", options = "持续情绪低落|偶尔心情不好|一时冲动|正常情绪波动", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "建立健康的人际关系需要什么？", options = "相互尊重与信任|控制对方|一味付出|保持距离", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "面对挫折时，积极的心态应该是？", options = "从中学习和成长|自怨自艾|责怪他人|放弃努力", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "以下哪种行为有助于改善睡眠质量？", options = "规律作息时间|睡前玩手机|大量摄入咖啡因|熬夜补觉", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "当朋友向你倾诉烦恼时，最好的做法是？", options = "倾听并提供支持|立即给出建议|转移话题|比较自己的经历", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "如何有效管理压力？", options = "制定合理计划和时间管理|拖延应对|过度工作|忽视问题", correctIndex = 0, category = "MENTAL_HEALTH"),
            QuizQuestionEntity(question = "自我价值感来源于哪里？", options = "内在认同和成就|他人的评价|物质拥有|社交媒体点赞", correctIndex = 0, category = "MENTAL_HEALTH")
        )
    }
}
