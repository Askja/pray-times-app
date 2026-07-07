package ru.admiral.praytimes.holiday

import ru.admiral.praytimes.R

enum class IslamicHoliday(
    val month: Int,
    val day: Int,
    val titleRes: Int,
    val backgroundRes: Int,
) {
    ISLAMIC_NEW_YEAR(1, 1, R.string.holiday_islamic_new_year, R.drawable.holiday_bg_islamic_new_year),
    ASHURA(1, 10, R.string.holiday_ashura, R.drawable.holiday_bg_ashura),
    MAWLID(3, 12, R.string.holiday_mawlid, R.drawable.holiday_bg_mawlid),
    ISRA_MIRAJ(7, 27, R.string.holiday_isra_miraj, R.drawable.holiday_bg_isra_miraj),
    NISF_SHABAN(8, 15, R.string.holiday_nisf_shaban, R.drawable.holiday_bg_nisf_shaban),
    RAMADAN_BEGINS(9, 1, R.string.holiday_ramadan_begins, R.drawable.holiday_bg_ramadan_begins),
    LAYLAT_AL_QADR(9, 27, R.string.holiday_laylat_al_qadr, R.drawable.holiday_bg_laylat_al_qadr),
    EID_AL_FITR(10, 1, R.string.holiday_eid_al_fitr, R.drawable.holiday_bg_eid_al_fitr),
    ARAFAH(12, 9, R.string.holiday_arafah, R.drawable.holiday_bg_arafah),
    EID_AL_ADHA(12, 10, R.string.holiday_eid_al_adha, R.drawable.holiday_bg_eid_al_adha),
}
