package gov.anzong.androidnga.activity.compose.board

import com.alibaba.fastjson2.JSON
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.common.PreferenceKey

/** Pure stable-id order rules shared by loading, drag transactions, and tests. */
internal object HomeBoardOrderResolver {

    fun resolve(defaultIds: List<String>, savedIds: List<String>?): List<String> {
        val defaults = defaultIds.distinct()
        if (savedIds.isNullOrEmpty()) {
            return defaults
        }

        val available = defaults.toHashSet()
        val used = HashSet<String>(defaults.size)
        return buildList(defaults.size) {
            savedIds.forEach { id ->
                if (id in available && used.add(id)) {
                    add(id)
                }
            }
            defaults.forEach { id ->
                if (used.add(id)) {
                    add(id)
                }
            }
        }
    }

    fun decodeSavedIds(value: String?): List<String>? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            JSON.parseArray(value, String::class.java)?.toList()
        } catch (_: Exception) {
            null
        }
    }

    fun move(items: MutableList<String>, from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) {
            return false
        }
        items.add(to, items.removeAt(from))
        return true
    }

    fun hasSameOrder(first: List<String>, second: List<String>): Boolean = first == second

    fun restoreIfCurrent(
        items: MutableList<String>,
        expectedCurrent: List<String>,
        snapshot: List<String>,
    ): Boolean {
        if (!hasSameOrder(items, expectedCurrent)) {
            return false
        }
        items.clear()
        items.addAll(snapshot)
        return true
    }

    fun preferenceValue(defaultIds: List<String>, order: List<String>): String? {
        val defaults = resolve(defaultIds, null)
        val resolved = resolve(defaults, order)
        return if (resolved == defaults) null else JSON.toJSONString(resolved)
    }
}

/** App-wide preference storage; the fixed bookmark page is never included. */
internal object HomeBoardOrderStore {

    fun load(defaultIds: List<String>): List<String> {
        val savedIds = try {
            HomeBoardOrderResolver.decodeSavedIds(
                PreferenceUtils.getData(PreferenceKey.KEY_HOME_BOARD_ORDER, "")
            )
        } catch (_: Exception) {
            null
        }
        return HomeBoardOrderResolver.resolve(defaultIds, savedIds)
    }

    fun save(defaultIds: List<String>, order: List<String>): Boolean {
        val value = HomeBoardOrderResolver.preferenceValue(defaultIds, order)
        val editor = PreferenceUtils.edit()
        if (value == null) {
            editor.remove(PreferenceKey.KEY_HOME_BOARD_ORDER)
        } else {
            editor.putString(PreferenceKey.KEY_HOME_BOARD_ORDER, value)
        }
        return editor.commit()
    }
}
