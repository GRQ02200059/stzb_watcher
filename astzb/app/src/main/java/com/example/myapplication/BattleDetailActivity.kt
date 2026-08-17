package com.example.myapplication

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class BattleDetailActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var resultBadgeView: TextView
    private lateinit var summaryView: TextView
    private lateinit var attackerView: TextView
    private lateinit var defenderView: TextView
    private lateinit var heroesView: TextView
    private lateinit var extraView: TextView
    private lateinit var contentView: TextView
    private var battleId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LocalTrialManager.ensureAccessOrRedirect(this)) {
            return
        }
        setContentView(R.layout.activity_battle_detail)
        StatusBarInsetHelper.applyTopSafeSpacing(findViewById(R.id.battleDetailRoot))

        titleView = findViewById(R.id.battleDetailTitleView)
        subtitleView = findViewById(R.id.battleDetailSubtitleView)
        resultBadgeView = findViewById(R.id.battleDetailResultBadgeView)
        summaryView = findViewById(R.id.battleDetailSummaryView)
        attackerView = findViewById(R.id.battleDetailAttackerView)
        defenderView = findViewById(R.id.battleDetailDefenderView)
        heroesView = findViewById(R.id.battleDetailHeroesView)
        extraView = findViewById(R.id.battleDetailExtraView)
        contentView = findViewById(R.id.battleDetailContentView)
        findViewById<TextView>(R.id.btnRefreshPage).setOnClickListener {
            loadBattleDetail()
        }

        HeroNameResolver.init(applicationContext)
        SkillNameResolver.init(applicationContext)
        LocalStzbRepository.init(applicationContext)
        battleId = intent.getIntExtra(EXTRA_BATTLE_ID, 0)
        titleView.text = "战报详情 #$battleId"

        if (battleId <= 0) {
            showMessage("缺少战报参数")
            return
        }

        loadBattleDetail()
    }

    private fun loadBattleDetail() {
        if (battleId <= 0) {
            showMessage("缺少战报参数")
            return
        }
        showMessage("加载中...")
        thread(name = "stzb-battle-detail") {
            val result = runCatching {
                LocalStzbRepository.loadFullBattle(battleId) ?: LocalStzbRepository.loadBattleNotice(battleId)
            }
            runOnUiThread {
                result.fold(
                    onSuccess = { battle ->
                        when (battle) {
                            null -> showMessage("本机库中没有找到战报 #$battleId")
                            is LocalFullBattle -> renderFullDetail(battle)
                            is LocalBattleNotice -> renderDetail(battle)
                            else -> showMessage("不支持的战报类型：${battle::class.java.simpleName}")
                        }
                    },
                    onFailure = { showMessage("加载失败：${it.message}") },
                )
            }
        }
    }

    private fun renderFullDetail(battle: LocalFullBattle) {
        val attackerHeroes = battle.attackerHeroes.toHeroLines("攻方")
        val defenderHeroes = battle.defenderHeroes.toHeroLines("守方")
        titleView.text = "战报详情 #${battle.battleId}"
        subtitleView.text = "${formatTime(battle.time)}  ·  ${localFightTypeText(battle.fightType)}  ·  来源 ${battle.sourceMsgId}"
        renderResultBadge(battle.result)
        summaryView.text = """
            ${battle.attackerName.ifBlank { "未知攻方" }}  →  ${battle.defenderName.ifBlank { battle.defenderUnion.ifBlank { "未知守方" } }}
            wid ${battle.wid}  ·  ${battle.widName.ifBlank { battle.widCode.ifBlank { "未命名地块" } }}
            武勋 ${battle.attackerGongxun}  ·  天气 ${battle.weather}  ·  夜战 ${if (battle.inNight != 0) "是" else "否"}  ·  NPC ${if (battle.isNpc != 0) "是" else "否"}
        """.trimIndent()
        attackerView.text = """
            攻方
            ${battle.attackerName.ifBlank { "未知" }}
            UID：${battle.attackerUid.ifBlank { "-" }}
            同盟：${battle.attackerUnion.ifBlank { "-" }}
            势力：${battle.attackerPower}
            武勋：${battle.attackerGongxun}
            兵力：${battle.attackerHp}
            队伍：${battle.attackerTeamId}
        """.trimIndent()
        defenderView.text = """
            守方
            ${battle.defenderName.ifBlank { "未知" }}
            UID：${battle.defenderUid.ifBlank { "-" }}
            同盟：${battle.defenderUnion.ifBlank { "-" }}
            等级：${battle.defenderLevel}
            势力：${battle.defenderPower}
            兵力：${battle.defenderHp}
            队伍：${battle.defenderTeamId}
        """.trimIndent()
        heroesView.text = """
            攻方武将
            $attackerHeroes

            守方武将
            $defenderHeroes
        """.trimIndent()
        extraView.text = """
            扩展字段
            攻方进阶：${battle.attackerAdvance.ifBlank { "-" }}
            守方进阶：${battle.defenderAdvance.ifBlank { "-" }}
            攻方兵种：${battle.attackerHeroType.ifBlank { "-" }}
            守方兵种：${battle.defenderHeroType.ifBlank { "-" }}
            城池类型：${battle.cityType}  ·  驻守：${battle.garrison}  ·  借地：${battle.borrowLand}
        """.trimIndent()
        contentView.text = "raw\n${battle.rawJson.take(2500)}"
    }

    private fun renderDetail(notice: LocalBattleNotice) {
        val heroLines = notice.heroLines()
            .mapIndexed { idx, hero -> "  ${idx + 1}. $hero" }
            .joinToString("\n")
            .ifBlank { "  834 通知中暂无武将明细" }

        titleView.text = "战报详情 #${notice.battleId}"
        subtitleView.text = "${formatTime(notice.time)}  ·  ${localFightTypeText(notice.fightType)}  ·  来源 2100 通知"
        renderResultBadge(notice.result)
        summaryView.text = """
            ${notice.attackerName.ifBlank { "未知攻方" }}  →  ${notice.defenderName.ifBlank { notice.defenderUnion.ifBlank { "未知守方" } }}
            wid ${notice.wid}  ·  ${notice.widCode.ifBlank { "通知战报" }}
            武勋 ${notice.attackerGongxun}  ·  攻方势力 ${notice.attackerPower}
        """.trimIndent()
        attackerView.text = """
            攻方
            ${notice.attackerName.ifBlank { "未知" }}
            UID：${notice.attackerUid.ifBlank { "-" }}
            武勋：${notice.attackerGongxun}
            势力：${notice.attackerPower}
        """.trimIndent()
        defenderView.text = """
            守方
            ${notice.defenderName.ifBlank { "-" }}
            同盟：${notice.defenderUnion.ifBlank { "-" }}
            等级：${notice.defenderLevel}
            武勋：${notice.defenderGongxun}
        """.trimIndent()
        heroesView.text = "武将\n$heroLines"
        extraView.text = "说明\n当前详情来自 2100 通知包，字段有限；捕获到 10/92 完整战报后会展示完整攻守双方、兵力和扩展字段。"
        contentView.text = "heroes_json\n${notice.heroesJson.ifBlank { "-" }}"
    }

    private fun showMessage(message: String) {
        subtitleView.text = message
        summaryView.text = message
        attackerView.text = "攻方\n--"
        defenderView.text = "守方\n--"
        heroesView.text = "武将\n--"
        extraView.text = "扩展字段\n--"
        contentView.text = ""
    }

    private fun renderResultBadge(result: Int) {
        resultBadgeView.text = localResultText(result)
        resultBadgeView.setBackgroundColor(
            when (result) {
                1, 7, 11 -> 0xFF16A34A.toInt()
                0, 10 -> 0xFFF59E0B.toInt()
                else -> 0xFFDC2626.toInt()
            }
        )
    }

    private fun formatTime(ts: Long): String {
        if (ts <= 0L) return "--:--"
        val millis = if (ts < 10_000_000_000L) ts * 1000 else ts
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))
    }

    companion object {
        const val EXTRA_BATTLE_ID = "battle_id"
    }

    private fun List<LocalBattleHero>.toHeroLines(label: String): String {
        return sortedBy { it.pos }.joinToString("\n") { hero ->
            val name = hero.heroName.ifBlank { "武将${hero.heroId}" }
            "  $label ${hero.pos + 1}号位：$name Lv.${hero.level} 进阶${hero.star} HP ${hero.remainHp}/${hero.maxHp}"
        }.ifBlank { "  暂无武将明细" }
    }
}
