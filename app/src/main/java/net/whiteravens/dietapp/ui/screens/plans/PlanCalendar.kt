// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.PlanCalendarDay
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

private val GAP = 3.dp
private val CELL = RoundedCornerShape(6.dp)

/**
 * The plans of a profile as a calendar: a month at a time, Monday first. A day that a plan holds
 * says what it holds, and choosing it opens its plan at that day; a day that none holds is where a
 * new plan can begin. Each day says in words what its cell shows, to somebody who does not see it.
 */
@Composable
internal fun PlanCalendar(
    calendar: MealPlansViewModel.Calendar,
    today: LocalDate,
    onLeaf: (months: Long) -> Unit,
    onThisMonth: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (planId: String, date: LocalDate) -> Unit,
    onStart: (date: LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    AppCard {
        Text(stringResource(R.string.plans_calendar_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.plans_calendar_lead),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                calendar.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            IconButton(onClick = { onLeaf(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.plans_calendar_previous))
            }
            TextButton(onClick = onThisMonth, enabled = calendar.month != YearMonth.from(today)) {
                Text(stringResource(R.string.plans_calendar_this_month))
            }
            IconButton(onClick = { onLeaf(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.plans_calendar_next))
            }
        }
        // The month before stays drawn while the next is on its way, with a line that says it is coming.
        Box(Modifier.fillMaxWidth().heightIn(min = 2.dp).padding(bottom = 6.dp)) {
            if (calendar.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        val error = calendar.error
        if (error != null) {
            ErrorBanner(errorMessage(error))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                calendar.weeks.first().forEach { date ->
                    val long = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).semantics { contentDescription = long },
                    )
                }
            }
            calendar.weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.padding(top = GAP)) {
                    week.forEach { date ->
                        val day = calendar.days[date]
                        DayCell(
                            date = date,
                            day = day,
                            inMonth = YearMonth.from(date) == calendar.month,
                            isToday = date == today,
                            onClick = { if (day != null) onOpen(day.planId, date) else onStart(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // What the marks in a day mean is said in words: a mark alone says it to nobody who does not already know.
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Legend(stringResource(R.string.plans_calendar_legend_day))
            Legend(stringResource(R.string.plans_calendar_legend_off), Icons.Default.Warning, MaterialTheme.colorScheme.tertiary)
            Legend(stringResource(R.string.plans_calendar_legend_eaten), Icons.Default.Check)
            Legend(stringResource(R.string.plans_calendar_legend_skipped))
            Legend(stringResource(R.string.plans_calendar_legend_more))
        }
    }
}

/**
 * One day: its number, ringed when it is today; for a day of a plan, what it holds in kcal (marked
 * where it is more than a tenth off its target, a dash where the plan skips it), the meals eaten and
 * how many more plans hold it. A day of the month before or after is told apart by a dashed frame
 * and a quieter colour that can still be read.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    day: PlanCalendarDay?,
    inMonth: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val frame = if (day != null) colors.primary.copy(alpha = 0.4f) else colors.outlineVariant
    val spoken = spokenDay(date, day)
    val small = MaterialTheme.typography.labelSmall
    val quiet = colors.onSurfaceVariant
    Column(
        modifier
            .heightIn(min = 60.dp)
            .clip(CELL)
            .background(if (day != null) colors.primary.copy(alpha = 0.06f) else Color.Transparent)
            .then(if (inMonth) Modifier.border(1.dp, frame, CELL) else Modifier.dashedFrame(frame))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(GAP),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).then(if (isToday) Modifier.background(colors.primary, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    style = small,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isToday -> colors.onPrimary
                        inMonth -> colors.onSurface
                        else -> quiet
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (day != null && day.plans > 1) Fitted("+${day.plans - 1}", quiet)
        }
        when {
            day == null -> Unit
            day.skipped -> Fitted("—", quiet)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                val off = day.offTarget != null
                if (off) Icon(Icons.Default.Warning, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(9.dp))
                // Without grouping: a cell has the room for the figures and no more.
                Fitted(day.calories.roundToInt().toString(), if (off) colors.tertiary else colors.onSurface, if (off) FontWeight.SemiBold else null)
            }
        }
        if (day != null && day.mealsEaten > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = quiet, modifier = Modifier.size(9.dp))
                Fitted("${day.mealsEaten}/${day.meals}", quiet)
            }
        }
    }
}

/** A cell's figures at the size that fits its room: smaller where the font is set large, never cut. */
@Composable
private fun Fitted(text: String, color: Color, weight: FontWeight? = null) {
    BasicText(
        text,
        style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = weight),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 10.sp),
    )
}

/** What a day says to somebody who does not see its cell. */
@Composable
private fun spokenDay(date: LocalDate, day: PlanCalendarDay?): String {
    val name = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", LocalConfiguration.current.locales[0]))
    if (day == null) return stringResource(R.string.plans_calendar_day_free, name)
    val others = day.plans - 1
    val more = if (others > 0) pluralStringResource(R.plurals.plans_calendar_more, others, others) else ""
    if (day.skipped) return stringResource(R.string.plans_calendar_day_skipped, name, more)
    val standing = when (day.offTarget) {
        "over" -> stringResource(R.string.plans_calendar_over)
        "under" -> stringResource(R.string.plans_calendar_under)
        else -> ""
    }
    val eaten = pluralStringResource(R.plurals.plans_calendar_eaten, day.meals, formatNumber(day.mealsEaten), day.meals)
    return stringResource(
        R.string.plans_calendar_day_planned,
        name,
        formatNumber(day.calories.roundToInt()),
        formatNumber(day.target.roundToInt()),
        standing,
        eaten,
        more,
    )
}

@Composable
private fun Legend(text: String, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 4.dp).size(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A frame of dashes around a cell, for a day of the month before or after. */
private fun Modifier.dashedFrame(color: Color) = drawBehind {
    val width = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}
