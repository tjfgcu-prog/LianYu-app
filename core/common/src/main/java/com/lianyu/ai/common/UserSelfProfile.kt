package com.lianyu.ai.common

/** 角色眼中的"我"。空字符串表示未填。 */
data class UserSelfProfile(
    val name: String = "",
    val gender: String = "",
    val age: String = "",
    val occupation: String = "",
    val callName: String = "",
    val note: String = ""
) {
    /** 本对象里填了的项优先，没填的沿用 [base]。 */
    fun overlay(base: UserSelfProfile) = UserSelfProfile(
        name = name.ifBlank { base.name },
        gender = gender.ifBlank { base.gender },
        age = age.ifBlank { base.age },
        occupation = occupation.ifBlank { base.occupation },
        callName = callName.ifBlank { base.callName },
        note = note.ifBlank { base.note }
    )

    /** 注入提示词的文本；全空返回空串。 */
    fun toPromptText(): String {
        val lines = buildList {
            if (name.isNotBlank()) add("姓名：$name")
            if (gender.isNotBlank()) add("性别：$gender")
            if (age.isNotBlank()) add("年龄：$age")
            if (occupation.isNotBlank()) add("职业/身份：$occupation")
            if (callName.isNotBlank()) add("希望被称呼为：$callName")
            if (note.isNotBlank()) add("补充说明：$note")
        }
        return if (lines.isEmpty()) "" else "【用户信息】\n" + lines.joinToString("\n")
    }

    companion object {
        const val NOTE_MAX = 200
    }
}
