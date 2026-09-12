package com.sspd.servicemgmt.core.util

object PasswordRules {
    const val MIN_LENGTH = 8
    const val HINT =
        "အနည်းဆုံး ၈ လုံး — အက္ခရာ၊ ဂဏန်း နှင့် အထူးအက္ခရာ (!@#\$…) ရောထည့်ပါ"

    fun isStrong(password: String): Boolean {
        if (password.length < MIN_LENGTH) return false
        var hasLetter = false
        var hasDigit = false
        var hasSpecial = false
        for (c in password) {
            when {
                c.isLetter() -> hasLetter = true
                c.isDigit() -> hasDigit = true
                !c.isWhitespace() -> hasSpecial = true
            }
        }
        return hasLetter && hasDigit && hasSpecial
    }
}
