// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.screens.recipes.RecipeShareRow
import net.whiteravens.dietapp.ui.screens.recipes.SharedLine
import net.whiteravens.dietapp.ui.screens.recipes.sharedRecipe
import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.ModelPart
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanPacks
import net.whiteravens.dietapp.domain.PlanWeek
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.domain.SwapOffer
import net.whiteravens.dietapp.domain.TargetMoved
import net.whiteravens.dietapp.domain.UseUpFound
import net.whiteravens.dietapp.domain.UseUpSwap
import net.whiteravens.dietapp.domain.fitsDiet
import net.whiteravens.dietapp.ui.common.Bound
import net.whiteravens.dietapp.ui.common.MARK
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.emphasized
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.fallbackText
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.formatSigned
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.common.refusalText
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AiMark
import net.whiteravens.dietapp.ui.components.LabelGrid
import net.whiteravens.dietapp.ui.components.LabelLegend
import net.whiteravens.dietapp.ui.components.LabelLine
import net.whiteravens.dietapp.ui.components.LabelNotes
import net.whiteravens.dietapp.ui.components.AiOffNote
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.aiOffText
import net.whiteravens.dietapp.ui.components.inOneLine
import net.whiteravens.dietapp.ui.components.modelPartText
import net.whiteravens.dietapp.ui.components.rememberWebPages
import net.whiteravens.dietapp.ui.screens.diary.entryLine
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.components.DOT
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One plan: what it is and what a day of it holds on average, its days, and the selected day's
 * meals with what each says of itself; a meal opens as planned, and can be swapped.
 */
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onOpenRecipe: (recipeId: String, portion: Double, whole: Boolean) -> Unit,
    onTurnAiOn: () -> Unit,
    onOpenDiary: (profileId: String, date: String) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read again on being shown again: the plan may have been changed elsewhere in the meantime.
    WhenShownAgain(viewModel::shownAgain)
    val plan = state.shown
    var openMealId by rememberSaveable { mutableStateOf<String?>(null) }
    // The day a meal of one's own is being added to, while its form is open.
    var addingTo by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    // The plan as a table or on paper is the web application's: its page of the plan opens in the browser.
    val web = rememberWebPages()
    val readOnly = state.offline
    val busy = state.changing || state.taking

    val list = rememberLazyListState()
    // The meal a change was asked of. The list keeps its place by what it shows; should the meal
    // have left the screen all the same once the change has come back, the list goes back to it.
    var changedMeal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(plan, busy) {
        if (busy) return@LaunchedEffect
        val meal = changedMeal ?: return@LaunchedEffect
        changedMeal = null
        val meals = plan?.days?.getOrNull(state.selectedDay)?.meals ?: return@LaunchedEffect
        val at = meals.indexOfFirst { it.id == meal }
        if (at < 0) return@LaunchedEffect
        // The list as this plan draws it.
        withFrameNanos { }
        val layout = list.layoutInfo
        if (layout.visibleItemsInfo.none { it.key == meal }) {
            // After the meals stand the button that adds one and, where the plan can be changed, the pantry's box.
            val after = if (readOnly) 1 else 2
            list.animateScrollToItem(layout.totalItemsCount - after - meals.size + at)
        }
    }

    addingTo?.let { date ->
        // The window stays until the meal is added: a refusal is said by its fields.
        LaunchedEffect(state.customMeal?.added) {
            if (state.customMeal?.added == true) {
                addingTo = null
                viewModel.customMealClosed()
            }
        }
        CustomMealDialog(
            date = date,
            listed = plan?.days?.firstOrNull { it.date == date }?.listed == true,
            // A meal of one's own that waits to be added is not one the server can put another in the place of.
            meals = plan?.days?.firstOrNull { it.date == date }?.meals.orEmpty().filterNot { it.id in state.waitingAdditions },
            sent = state.customMeal,
            sending = state.changing,
            onAdd = { meal, replaces -> viewModel.addCustomMeal(date, meal, replaces) },
            onDismiss = {
                addingTo = null
                viewModel.customMealClosed()
            },
        )
    }

    // The meal whose removal from its day is to be confirmed, with what it does said in the window.
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    plan?.days?.flatMap { it.meals }?.firstOrNull { it.id == removing }?.let { meal ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.plans_remove_title, mealName(meal))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(if (meal.eaten) R.string.plans_remove_note_eaten else R.string.plans_remove_note))
                    plan.days.firstOrNull { day -> day.meals.any { it.id == meal.id } }?.let { day ->
                        apartNote(meal, day.date, LocalDate.now().toString())?.let { Text(it) }
                        ListNote(day.listed)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        viewModel.removeMeal(meal.id)
                    },
                ) { Text(stringResource(R.string.plans_remove_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // The day whose settings are open in a window, while it is open.
    var settingsFor by rememberSaveable { mutableStateOf<String?>(null) }
    plan?.days?.firstOrNull { it.id == settingsFor }?.let { day ->
        DaySettingsDialog(
            planId = plan.id,
            day = day,
            week = plan.weeks.weekOf(day.date),
            onSaved = { saved ->
                settingsFor = null
                viewModel.dayTaken(saved)
            },
            onStale = viewModel::readAgain,
            onDismiss = { settingsFor = null },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            text = {
                val joint = plan?.joint
                Text(
                    if (joint == null) stringResource(R.string.plans_delete_confirm)
                    else "${stringResource(R.string.plans_delete_confirm)} ${stringResource(R.string.plans_delete_joint_note, joint.profileName)}",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    plan?.days?.firstOrNull { day -> day.meals.any { it.id == openMealId } }?.let { day ->
        val meal = day.meals.first { it.id == openMealId }
        val toCook = mealToCook(plan, meal)
        MealDialog(
            meal = meal,
            date = day.date,
            toCook = toCook,
            cooking = mealsOfCooking(plan, meal),
            dietType = plan.dietType,
            onOpenRecipe = meal.recipeId?.let { id -> { openMealId = null; onOpenRecipe(id, toCook.servings, toCook.whole) } },
            onDismiss = { openMealId = null },
        )
    }

    // How much of the plan the window to make it again opens on, while it is open.
    var rerollScope by rememberSaveable { mutableStateOf<RerollScope?>(null) }
    var rerollDay by rememberSaveable { mutableStateOf<String?>(null) }
    var rerollHolds by rememberSaveable { mutableStateOf(false) }
    val openReroll: (RerollStart) -> Unit = { start ->
        rerollScope = start.scope
        rerollDay = start.day
        rerollHolds = start.holdLimits
    }
    rerollScope?.let { scope ->
        if (plan != null) {
            RerollDialog(
                plan = plan,
                start = RerollStart(scope, rerollDay, rerollHolds),
                onConfirm = { asked ->
                    rerollScope = null
                    viewModel.reroll(asked)
                },
                onDismiss = { rerollScope = null },
            )
        }
    }

    // The day other ways to fill are offered for, while their window is open.
    var waysFor by rememberSaveable { mutableStateOf<String?>(null) }
    plan?.days?.firstOrNull { it.id == waysFor }?.let { day ->
        DayWaysDialog(
            planId = plan.id,
            day = day,
            onTaken = { taken ->
                waysFor = null
                viewModel.dayTaken(taken)
            },
            onStale = viewModel::readAgain,
            onDismiss = { waysFor = null },
            onTurnAiOn = onTurnAiOn,
        )
    }

    // The meal an ingredient is being substituted in, while its window is open.
    var substitutingIn by rememberSaveable { mutableStateOf<String?>(null) }
    plan?.days?.flatMap { it.meals }?.firstOrNull { it.id == substitutingIn }?.let { meal ->
        SubstituteDialog(
            planId = plan.id,
            meal = meal,
            listed = plan.days.firstOrNull { day -> day.meals.any { it.id == meal.id } }?.listed == true,
            onApplied = { result ->
                substitutingIn = null
                changedMeal = meal.id
                viewModel.substituted(result, meal.id)
            },
            onDismiss = { substitutingIn = null },
            onTurnAiOn = onTurnAiOn,
        )
    }

    // The words of a recipe a substitution made, while their window is open.
    state.rewording?.let { recipeId ->
        RecipeTextDialog(recipeId = recipeId, onDone = viewModel::reworded, onDismiss = viewModel::stopRewording)
    }

    // The meal a favourite is being picked for, while the picker is open.
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    plan?.days?.flatMap { it.meals }?.firstOrNull { it.id == pickingFor }?.let { meal ->
        FavoritePicker(
            meal = meal,
            planDiet = plan.dietType,
            liked = state.liked,
            onPick = { recipeId, showingAll ->
                pickingFor = null
                viewModel.offerSwap(meal.id, SwapAsk("favorite", favoriteRecipeId = recipeId, allowOffDiet = showingAll))
            },
            onDismiss = { pickingFor = null },
        )
    }

    state.setWindow?.let { window ->
        if (plan != null) ApplySetDialog(plan, window, onApply = viewModel::applySet, onDismiss = viewModel::closeSetWindow)
    }

    state.offer?.let { offer ->
        SwapOfferDialog(
            offer = offer,
            listedDates = plan?.days.orEmpty().filter { it.listed }.map { it.date }.toSet(),
            offerDate = plan?.days?.firstOrNull { day -> day.meals.any { it.id == offer.mealId } }?.date,
            busy = state.taking || state.offering != null,
            onTake = { together ->
                changedMeal = offer.mealId
                viewModel.takeOffer(together)
            },
            onAnother = viewModel::anotherOffer,
            onDismiss = viewModel::dismissOffer,
        )
    }

    // The card of a rebalance stands over the end of the list: the list ends that much higher, so
    // that nothing of it stays under the card.
    var cardHeight by remember { mutableIntStateOf(0) }
    val shownCard = state.rebalance != null || state.lastSwap != null || state.notice != null
    val underCard = if (shownCard) with(LocalDensity.current) { cardHeight.toDp() + 16.dp } else 0.dp
    Box(Modifier.fillMaxSize()) {
        PullToRead(state.pulled, viewModel::pull) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + underCard),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Header(
                        plan,
                        onBack,
                        onTableAndPrint = { plan?.let { web.open("meal-plans", "profile" to it.profileId, "plan" to it.id) } },
                        onApplySet = { viewModel.openSetWindow() }.takeUnless { readOnly || busy },
                        onDelete = { confirmingDelete = true }.takeUnless { readOnly || busy },
                    )
                }

                state.error?.let {
                    item {
                        Column {
                            ErrorBanner(errorMessage(it))
                            if (plan == null) {
                                Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                        }
                    }
                }
                if (state.offline) item { OfflineAsOf(state.readAt, says = R.string.offline_readonly) }
                // A re-roll refused for something the user can allow: asked again with it allowed.
                state.refused?.let { refused ->
                    item {
                        OutlinedButton(onClick = viewModel::allowRefused, enabled = !busy) {
                            Text(
                                stringResource(
                                    if (refused.allow == PlanDetailViewModel.INCLUDE_PAST) R.string.plans_include_past_and_retry else R.string.plans_drop_lock_and_retry,
                                ),
                            )
                        }
                    }
                }
                state.inTheWay?.let { inTheWay ->
                    item { InTheWayBox(inTheWay.needed, busy = busy, onLetGiveWay = viewModel::letGiveWay, onCancel = viewModel::dismissInTheWay) }
                }

                if (plan == null) {
                    if (state.loading) {
                        item { Skeleton(Modifier.fillMaxWidth().height(40.dp)) }
                        items(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }
                    }
                    return@LazyColumn
                }

                item {
                    PlanFacts(
                        plan,
                        onReroll = openReroll.takeUnless { readOnly || busy },
                        useUp = if (readOnly) null else {
                            {
                                UseUpPanel(
                                    found = state.useUp,
                                    listedDates = plan.days.filter { it.listed }.map { it.date }.toSet(),
                                    finding = state.findingUseUp,
                                    aiOn = state.aiOn,
                                    busy = busy,
                                    onFind = viewModel::findUseUp,
                                    onTake = viewModel::takeUseUp,
                                    onTurnAiOn = onTurnAiOn,
                                )
                            }
                        },
                    )
                }
                if (plan.conflicts > 0) {
                    item { ErrorBanner(pluralStringResource(R.plurals.plans_conflicts_notice, plan.conflicts, plan.conflicts)) }
                }
                // The profile's target moved since the plan was made: both said, and taken from today at a press.
                plan.targetMoved?.let { moved ->
                    item(key = "target-moved") {
                        val today = LocalDate.now().toString()
                        TargetMovedNote(
                            moved,
                            listed = plan.days.any { it.date >= today && !it.skipped && it.listed },
                            enabled = !readOnly && !busy,
                            onTake = viewModel::retarget,
                        )
                    }
                }

                item {
                    val chips = rememberLazyListState()
                    // The day the plan opened at is in sight, however far into the plan it lies.
                    LaunchedEffect(plan.id) { chips.scrollToItem(state.selectedDay) }
                    // And so is a day chosen later, from wherever it was chosen.
                    LaunchedEffect(state.selectedDay) {
                        val layout = chips.layoutInfo
                        val shown = layout.visibleItemsInfo.any { it.index == state.selectedDay && it.offset >= 0 && it.offset + it.size <= layout.viewportEndOffset }
                        if (!shown) chips.animateScrollToItem(state.selectedDay)
                    }
                    // By the phone's date.
                    val todayAt = plan.days.indexOfFirst { it.date == LocalDate.now().toString() }
                    LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(plan.days, key = { _, day -> day.id }) { index, day ->
                            FilterChip(
                                selected = index == state.selectedDay,
                                onClick = { viewModel.selectDay(index) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(formatDayDate(day.date))
                                        if (index == todayAt) {
                                            LabelChip(
                                                stringResource(R.string.plans_today),
                                                container = MaterialTheme.colorScheme.primary,
                                                content = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                val day = plan.days.getOrNull(state.selectedDay) ?: return@LazyColumn
                val today = LocalDate.now().toString()
                // The week of the day, where a change of one of its days left it off the plan's target.
                plan.weeks.weekOf(day.date)?.takeIf { it.uneven && it.average != null }?.let { week ->
                    item(key = "week-${week.from}") {
                        WeekLine(week, enabled = !readOnly && !busy, onEven = { viewModel.evenWeek(week) })
                    }
                }
                // Another day from today on can take a part of what this one is off its target by.
                val spreadable = plan.days.any { it.id != day.id && it.date >= today && !it.skipped }
                item(key = "summary-${day.id}") {
                    DaySummary(
                        day,
                        over = day.date < today,
                        // A day that is over keeps its dishes.
                        onOtherWays = { waysFor = day.id }.takeUnless { readOnly || busy || day.date < today },
                        onChange = { openReroll(RerollStart(RerollScope.DAY, day = day.date)) }.takeUnless { readOnly || busy || day.date < today },
                        // A day before today is history, and an instance without day settings has none to change.
                        onSettings = { settingsFor = day.id }.takeUnless { readOnly || busy || day.settings == null || day.date < today },
                        onSpread = { viewModel.spread(if (day.date < today) day.date else today, on = day.date) }.takeUnless { readOnly || busy || !spreadable },
                        spreadListed = plan.days.any { it.listed && !it.skipped && it.date >= (if (day.date < today) day.date else today) },
                        // A long plan is a long way along its days to the one it is now: one press takes the reader there.
                        onToday = plan.days.indexOfFirst { it.date == today }.takeIf { it >= 0 && day.date != today }?.let { at -> { viewModel.selectDay(at) } },
                        // The diary of a day that has come.
                        onDiary = { onOpenDiary(plan.profileId, day.date) }.takeIf { day.date <= today },
                        // A day of today or after it is resized with its own meals.
                        onResize = { viewModel.resizeDay(day.date) }.takeUnless {
                            readOnly || busy || day.date < today || day.meals.none { meal -> !meal.eaten && meal.recipeId != null && !meal.custom }
                        },
                        resizeLeft = state.resizeLeft?.takeIf { it.first == day.date }?.second,
                    )
                }
                val written = day.diary?.items.orEmpty()
                // The diary of a day that has come opens from what it holds.
                val openDiary = { onOpenDiary(plan.profileId, day.date) }.takeIf { day.date <= today }
                // What the diary holds for a meal the day has none of stands where that meal would in the day.
                val apart = entriesApart(day.meals, written)
                day.meals.forEachIndexed { index, meal ->
                    apart[index].orEmpty().forEach { entry ->
                        item(key = "entry-${entry.id}") { DiaryEntryCard(entry, openDiary) }
                    }
                    item(key = meal.id) {
                        MealCard(
                            meal = meal,
                            date = day.date,
                            today = today,
                            // A meal of a day that is over, or that is skipped, keeps its dish.
                            changeable = !day.skipped && day.date >= today,
                            parted = partedFrom(plan, plan.days.getOrNull(state.selectedDay - 1), meal, today),
                            swapping = meal.id == state.offering,
                            waiting = meal.id in state.waitingMeals,
                            added = meal.id in state.waitingAdditions,
                            readOnly = readOnly,
                            busy = busy,
                            aiOn = state.aiOn == true,
                            onEaten = { eaten ->
                                changedMeal = meal.id
                                viewModel.setEaten(meal.id, eaten)
                            },
                            onChange = { how -> viewModel.offerSwap(meal.id, how) },
                            onPickFavorite = { pickingFor = meal.id },
                            onSubstitute = { substitutingIn = meal.id },
                            onRemove = { removing = meal.id },
                            onOpen = { openMealId = meal.id },
                        )
                    }
                    // What the diary holds for this meal of the day follows its last meal of that kind.
                    if (day.meals.lastOrNull { it.slot == meal.slot } === meal) {
                        written.filter { it.slot == meal.slot }.forEach { entry ->
                            item(key = "entry-${entry.id}") { DiaryEntryCard(entry, openDiary) }
                        }
                    }
                }
                apart[-1].orEmpty().forEach { entry ->
                    item(key = "entry-${entry.id}") { DiaryEntryCard(entry, openDiary) }
                }
                item(key = "add-${day.id}") {
                    TextButton(onClick = { addingTo = day.date }, enabled = !busy) { Text(stringResource(R.string.plans_add_custom_meal)) }
                }
                if (!readOnly) {
                    // What every swap of the plan puts first: under the meals, out of their way.
                    item(key = "pantry") {
                        CheckRow(
                            label = stringResource(R.string.plans_swap_with_pantry),
                            hint = stringResource(R.string.plans_swap_with_pantry_hint),
                            checked = state.swapWithPantry,
                            onChange = viewModel::setSwapWithPantry,
                        )
                    }
                }
            }
        }
        if (shownCard) {
            // The day it speaks of: of the swap, or of the first meal sized.
            val about = state.lastSwap?.mealId ?: state.rebalance?.changes?.firstOrNull()?.mealId
            RebalanceCard(
                rebalance = state.rebalance,
                swapRevision = state.lastSwap?.revision,
                day = state.plan?.days?.firstOrNull { day -> day.meals.any { it.id == about } },
                notice = state.notice,
                busy = busy,
                onUndo = viewModel::undoRebalance,
                onUndoSwap = viewModel::undoSwap,
                reword = state.lastSwap?.reword,
                onReword = viewModel::reword,
                onRest = { viewModel.spread() },
                onDismiss = viewModel::dismissRebalance,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).onSizeChanged { cardHeight = it.height },
            )
        }
    }
}

/** What the plan is, when it begins, what a day of it holds on average, and whether a model had a hand in it; and what can be done with it. */
@Composable
private fun Header(plan: MealPlan?, onBack: () -> Unit, onTableAndPrint: () -> Unit, onApplySet: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
        }
        Column(Modifier.padding(start = 4.dp).weight(1f)) {
            Text(
                if (plan != null) planSummary(plan.durationDays, plan.dietType) else stringResource(R.string.nav_meal_plans),
                style = MaterialTheme.typography.titleLarge,
            )
            if (plan != null) {
                Text(
                    planMeta(plan.startDate, plan.averageDailyNutrition.calories, plan.ingredientReuse),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                plan.joint?.let { joint ->
                    Text(
                        stringResource(R.string.plans_joint_with, joint.profileName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plan.byModel) AiMark(stringResource(R.string.ai_mark_plan), Modifier.padding(top = 6.dp), spelledOut = true)
            }
        }
        if (plan != null) PlanActions(onTableAndPrint, onApplySet, onDelete)
    }
}

/**
 * What can be done with the plan as a whole, behind one button: a favourite set put on its days,
 * the plan as a table or printed in the web application, or the plan deleted.
 */
@Composable
private fun PlanActions(onTableAndPrint: () -> Unit, onApplySet: (() -> Unit)?, onDelete: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.plans_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(action: () -> Unit) {
                open = false
                action()
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.favorite_sets_apply)) }, onClick = { onApplySet?.let(::pick) }, enabled = onApplySet != null)
            DropdownMenuItem(text = { Text(stringResource(R.string.web_table_and_print)) }, onClick = { pick(onTableAndPrint) })
            DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { onDelete?.let(::pick) }, enabled = onDelete != null)
        }
    }
}

/**
 * What a day of the plan holds against its targets, the packs its menu opens and does not finish,
 * the limits it was let go outside of, and what the pantry had to do with it.
 */
@Composable
private fun PlanFacts(plan: MealPlan, onReroll: ((RerollStart) -> Unit)?, useUp: (@Composable () -> Unit)?) {
    val average = plan.averageDailyNutrition
    val target = plan.targetMacros
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Fact(
                stringResource(
                    R.string.plans_plan_macros,
                    formatNumber(average.protein.roundToInt()),
                    formatNumber(target.protein.roundToInt()),
                    formatNumber(average.fat.roundToInt()),
                    formatNumber(target.fat.roundToInt()),
                    formatNumber(average.carbs.roundToInt()),
                    formatNumber(target.carbs.roundToInt()),
                ),
            )
            plan.averageOff?.let { off ->
                Fact(stringResource(if (off == "over") R.string.plans_average_over else R.string.plans_average_under), color = MaterialTheme.colorScheme.tertiary)
            }
            if (plan.averageMacrosOff) Fact(stringResource(R.string.plans_average_macros), color = MaterialTheme.colorScheme.tertiary)
            if (plan.apart.isNotEmpty()) {
                val locale = LocalConfiguration.current.locales[0]
                Fact(stringResource(R.string.plans_joint_apart, formatList(plan.apart.map { mealTypeLabel(it).lowercase(locale) })))
            }
            if (plan.packs.bought > 0) PacksLine(plan.packs, useUp.takeIf { plan.packs.open.any { it.wasted } })
            if (plan.mayGiveWay.isNotEmpty()) {
                Fact(stringResource(R.string.plans_may_give_way, limitNames(plan.mayGiveWay)))
                if (onReroll != null) {
                    TextButton(onClick = { onReroll(RerollStart(RerollScope.WHOLE, holdLimits = true)) }) { Text(stringResource(R.string.plans_hold_limits)) }
                }
            }
            when (plan.pantry) {
                "counted" -> Fact(stringResource(R.string.plans_pantry_counted))
                "left_out" -> Fact(stringResource(R.string.plans_pantry_left_out))
                "unused" -> Fact(stringResource(R.string.plans_pantry_unused))
            }
            if (plan.lessWaste) Fact(stringResource(R.string.plans_less_waste_line))
            if (onReroll != null) {
                OutlinedButton(onClick = { onReroll(RerollStart(RerollScope.WHOLE)) }) { Text(stringResource(R.string.plans_recalculate)) }
            }
        }
    }
}

@Composable
private fun Fact(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** What the menu uses of the packs that do not keep once opened, unfolding into the packs it does not finish. */
@Composable
private fun PacksLine(packs: PlanPacks, useUp: (@Composable () -> Unit)?) {
    var open by rememberSaveable { mutableStateOf(false) }
    val percent = formatNumber((packs.used / packs.bought * 100).roundToInt())
    val wasted = packs.open.count { it.wasted }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .semantics { if (open) collapse { open = false; true } else expand { open = true; true } },
        ) {
            Fact(
                if (wasted == 0) stringResource(R.string.plans_packs_line, percent)
                else pluralStringResource(R.plurals.plans_packs_line_wasted, wasted, wasted, percent),
                Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (packs.open.isEmpty()) Fact(stringResource(R.string.plans_packs_all_finished))
                packs.open.forEach { pack ->
                    val left = amountLabel(pack.left.toDouble(), pack.unit)
                    val size = amountLabel(pack.packSize, pack.unit)
                    if (pack.wasted) Fact(stringResource(R.string.plans_packs_left_much, pack.name, left, size), color = MaterialTheme.colorScheme.tertiary)
                    else Fact(stringResource(R.string.plans_packs_left, pack.name, left, size))
                }
                Fact(stringResource(R.string.plans_packs_explain))
                useUp?.invoke()
            }
        }
    }
}

/**
 * The swaps that would use up what a plan opens, asked for under its line of packs: each with the
 * meal it is for, what it takes of the packs and what it does to the meal, a model's order and its
 * sentence on each where the user asks for it, and the one taken made as any swap is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UseUpPanel(
    found: UseUpFound?,
    /** The days of the plan a shopping list covers. */
    listedDates: Set<String>,
    finding: Boolean,
    aiOn: Boolean?,
    busy: Boolean,
    onFind: (weigh: Boolean) -> Unit,
    onTake: (UseUpSwap) -> Unit,
    onTurnAiOn: () -> Unit,
) {
    val working = busy || finding
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Side by side where the screen is wide enough for both.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onFind(false) }, enabled = !working) {
                Text(stringResource(if (finding && found == null) R.string.plans_use_up_asking else R.string.plans_use_up_ask))
            }
            if (found?.swaps?.isNotEmpty() == true) {
                TextButton(onClick = { onFind(true) }, enabled = aiOn == true && !working) {
                    Text(stringResource(if (finding) R.string.weigh_asking else R.string.weigh_ask))
                }
            }
        }
        if (found?.swaps?.isNotEmpty() == true) {
            if (aiOn == false) AiOffNote(onTurnAiOn) else Fact(stringResource(R.string.weigh_tooltip))
        }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when {
                found == null -> Unit
                found.weighed -> Fact(stringResource(R.string.weigh_weighed))
                found.fallbackReason != null -> fallbackText(found.fallbackReason)?.let { Fact(it, color = MaterialTheme.colorScheme.tertiary) }
            }
        }
        if (found != null && found.swaps.isEmpty()) Fact(stringResource(R.string.plans_use_up_none))
        found?.swaps?.forEach { swap ->
            val takes = swap.takes.map { taken ->
                stringResource(R.string.plans_use_up_takes, amountLabel(taken.amount.toDouble(), taken.unit), taken.name, amountLabel(taken.left.toDouble(), taken.unit))
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("${formatDayDate(swap.date)}$DOT${mealTypeLabel(swap.slot)}: ")
                            append(emphasized(stringResource(R.string.plans_use_up_swap, MARK, swap.fromTitle), swap.toTitle))
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    swap.reason?.let { AiMark(stringResource(R.string.ai_mark_weighed, it), spelledOut = true) }
                    Fact(takes.joinToString(DOT))
                    ListNote(swap.date in listedDates)
                    Fact(
                        stringResource(
                            R.string.plans_use_up_energy,
                            formatNumber(swap.before.calories.roundToInt()),
                            formatNumber(swap.after.calories.roundToInt()),
                            formatNumber(swap.before.protein.roundToInt()),
                            formatNumber(swap.after.protein.roundToInt()),
                        ),
                    )
                    Button(onClick = { onTake(swap) }, enabled = !working) { Text(stringResource(R.string.plans_offer_take)) }
                }
            }
        }
    }
}

/**
 * A day against its target: its kind and the settings it has of its own, what it holds in kcal and
 * by how much it misses, its macros against theirs, and where it stands; a day off its target can
 * be spread over the remaining days of the plan, with what that does said beside it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySummary(
    day: PlanDay,
    over: Boolean,
    onOtherWays: (() -> Unit)?,
    onChange: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    onSpread: (() -> Unit)?,
    /** A shopping list covers a day that spreading changes. */
    spreadListed: Boolean = false,
    onToday: (() -> Unit)?,
    onDiary: (() -> Unit)?,
    onResize: (() -> Unit)?,
    resizeLeft: Double?,
) {
    AppCard {
        val kind = day.settings?.dayType
        if (kind == "training" || kind == "rest") {
            PlainChip(stringResource(if (kind == "training") R.string.plans_day_kind_chip_training else R.string.plans_day_kind_chip_rest))
        }
        if (day.skipped) {
            // A day the user skips is no shortfall, and there is nothing in it to fill; its settings can bring it back.
            Text(stringResource(R.string.plans_day_skipped), style = MaterialTheme.typography.titleMedium)
            onSettings?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_day_settings)) } }
            onToday?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_go_to_today)) } }
            onDiary?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_open_diary)) } }
            return@AppCard
        }
        daySettingsLine(day)?.let { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(
            stringResource(
                R.string.plans_calorie_line,
                formatNumber(day.nutrition.calories.roundToInt()),
                formatNumber(day.calorieTarget.roundToInt()),
                formatSigned(day.calorieDelta),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (day.offTarget == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(
                R.string.plans_day_macros_of,
                formatNumber(day.nutrition.protein.roundToInt()),
                formatNumber(day.targetMacros.protein.roundToInt()),
                formatNumber(day.nutrition.fat.roundToInt()),
                formatNumber(day.targetMacros.fat.roundToInt()),
                formatNumber(day.nutrition.carbs.roundToInt()),
                formatNumber(day.targetMacros.carbs.roundToInt()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        day.label?.let { label ->
            LabelLine(label, Modifier.padding(top = 2.dp))
            LabelLegend(label, Modifier.padding(top = 2.dp))
        }
        LabelNotes(day.labelNotes, Modifier.padding(top = 4.dp))
        if (day.macrosOff) {
            Row(Modifier.padding(top = 4.dp)) { CautionChip(stringResource(R.string.plans_macros_off_chip)) }
            Fact(stringResource(R.string.plans_macros_off_hint), Modifier.padding(top = 4.dp))
        }
        if (over) Fact(stringResource(R.string.plans_day_is_over), Modifier.padding(top = 4.dp))
        // What the diary adds to the day is counted in the figures above.
        day.diary?.let { diary ->
            Text(
                pluralStringResource(R.plurals.plans_diary_line, diary.entries, diary.entries, formatNumber(diary.nutrition.calories.roundToInt())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val standing = day.offTarget
        if (standing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                CautionChip(stringResource(if (standing == "over") R.string.plans_day_over else R.string.plans_day_under))
            }
            // A day the diary put off its target has not been resized with it: nothing says that resizing failed.
            Text(
                stringResource(
                    when {
                        day.diary != null -> if (standing == "over") R.string.plans_day_over_diary_hint else R.string.plans_day_under_diary_hint
                        else -> if (standing == "over") R.string.plans_day_over_hint else R.string.plans_day_under_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            // What resizing does is said beside the button that does it, before it is pressed.
            onResize?.let { resize ->
                OutlinedButton(onClick = resize, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.plans_resize_day)) }
                Text(
                    stringResource(if (day.diary != null) R.string.plans_resize_day_note else R.string.plans_resize_day_note_plain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A press that changes nothing says so, or it looks as if it had not been heard.
            resizeLeft?.let { left ->
                Text(
                    when {
                        left == 0.0 -> stringResource(R.string.plans_resize_nothing)
                        left > 0 -> stringResource(R.string.plans_rebalance_left_over, formatNumber(left.roundToInt()))
                        else -> stringResource(R.string.plans_rebalance_left_under, formatNumber(abs(left).roundToInt()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            // What spreading does is said beside the button that does it, before it is pressed.
            onSpread?.let { spread ->
                OutlinedButton(onClick = spread, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.plans_spread)) }
                Text(
                    stringResource(if (standing == "over") R.string.plans_spread_note_over else R.string.plans_spread_note_under),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ListNote(spreadListed, several = true)
            }
        }
        if (day.belowSafetyFloor) {
            Text(
                stringResource(R.string.plans_day_below_floor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (onOtherWays != null || onChange != null || onSettings != null || onToday != null || onDiary != null) {
            FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                onOtherWays?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_other_ways)) } }
                onChange?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_change_day)) } }
                onSettings?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_day_settings)) } }
                onToday?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_go_to_today)) } }
                onDiary?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_open_diary)) } }
            }
        }
    }
}

/** The settings a day has of its own, in words: what makes it differ from the plan's other days; null where none does. */
@Composable
private fun daySettingsLine(day: PlanDay): String? {
    val settings = day.settings ?: return null
    val said = listOfNotNull(
        settings.calorieTarget?.let { stringResource(R.string.plans_settings_line_own_target, formatNumber(it)) }
            ?: when (settings.dayType) {
                "training" -> stringResource(R.string.plans_settings_line_training)
                "rest" -> stringResource(R.string.plans_settings_line_rest)
                else -> null
            },
        settings.cookTimeMinutes?.let { stringResource(R.string.plans_settings_line_cook_time, it) },
        if (settings.useUpBy) stringResource(R.string.plans_settings_line_use_up) else null,
    )
    return said.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

/** A meal's macros as a sentence, `Protein 25 g · Fat 10 g · Carbs 40 g · 420 kcal`, broken only after a dot. */
@Composable
private fun macrosLine(nutrition: Nutrition): String =
    inOneLine(
        listOf(
            "${stringResource(R.string.macros_protein)} ${formatNumber(nutrition.protein.roundToInt())}\u00A0g",
            "${stringResource(R.string.macros_fat)} ${formatNumber(nutrition.fat.roundToInt())}\u00A0g",
            "${stringResource(R.string.macros_carbs)} ${formatNumber(nutrition.carbs.roundToInt())}\u00A0g",
            "${formatNumber(nutrition.calories.roundToInt())}\u00A0kcal",
        ),
    )

/** A meal's name: its recipe's, the name its user gave it, or what a meal of one's own is called. */
@Composable
private fun mealName(meal: Meal): String = meal.name.ifEmpty { stringResource(R.string.plans_custom_meal) }

/**
 * A meal of the day: its time of day and the chips that say at a glance what there is to say of it,
 * its name and macros; one that is neither eaten nor the user's own can be swapped. It is marked
 * eaten or not and taken off its day without a network as well, where a change [waiting] to be sent
 * says so; one of one's own [added] and waiting is marked eaten once the server has it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealCard(
    meal: Meal,
    date: String,
    today: String,
    changeable: Boolean,
    parted: Meal?,
    swapping: Boolean,
    waiting: Boolean,
    added: Boolean,
    readOnly: Boolean,
    busy: Boolean,
    aiOn: Boolean,
    onEaten: (Boolean) -> Unit,
    onChange: (SwapAsk) -> Unit,
    onPickFavorite: () -> Unit,
    onSubstitute: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val markEaten = stringResource(R.string.plans_action_for, stringResource(R.string.plans_mark_eaten), mealName(meal))
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = meal.eaten,
                onCheckedChange = onEaten,
                // A meal is marked as eaten from its day on.
                enabled = !busy && !added && (meal.eaten || date <= today),
                modifier = Modifier.semantics { contentDescription = markEaten },
            )
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabelChip(mealTypeLabel(meal.slot))
                    meal.byModel?.let { AiMark(modelPartText(it)) }
                    if (meal.custom) LabelChip(stringResource(R.string.plans_custom_chip))
                    if (meal.estimated) LabelChip(stringResource(R.string.plans_estimated_chip))
                    if (meal.offDiet) CautionChip(stringResource(R.string.plans_off_diet))
                    meal.conflicts.forEach { conflict -> WarningChip(conflictLabel(conflict)) }
                    if (meal.disliked.isNotEmpty()) LabelChip(stringResource(R.string.plans_disliked))
                    if (meal.outsideLimits.isNotEmpty()) CautionChip(stringResource(R.string.plans_outside_limit_chip))
                    if (meal.onlyRecipe) LabelChip(stringResource(R.string.plans_only_recipe_chip))
                    // A small difference is the sizing of an ordinary day, and says nothing to the reader.
                    if (abs(meal.quantityScale - 1.0) >= AUTO_BALANCED_FROM) {
                        PlainChip(stringResource(R.string.plans_auto_balanced_chip, formatNumber(meal.quantityScale, fractionDigits = 2)))
                    }
                    cookChip(meal, date)?.let { PlainChip(it) }
                    if (meal.eaten) AccentChip(stringResource(R.string.plans_eaten))
                    if (waiting) WaitingMark()
                }
                Text(
                    mealName(meal),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    macrosLine(meal.nutrition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                notCookedNote(meal, date, today)?.let { Hint(it, MaterialTheme.colorScheme.tertiary) }
            }
            if (swapping) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                // Without a network a meal is only taken off its day.
                MealChanges(meal, parted, date, busy, aiOn, canSwap = !readOnly && changeable && !meal.eaten && !meal.custom, onChange, onPickFavorite, onSubstitute, onRemove)
            }
        }
    }
}

/**
 * An entry of the day's diary in a plan, under the meal it was written for: what it is, how much, what
 * it comes to and that it is the diary's, which changes and removes it. A press opens the diary of
 * that day, [onOpen], where the day has come.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryEntryCard(entry: DiaryEntry, onOpen: (() -> Unit)?) {
    AppCard(onClick = onOpen, modifier = Modifier.padding(start = 24.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabelChip(mealTypeLabel(entry.slot))
            PlainChip(stringResource(R.string.plans_from_diary_chip))
            if (entry.estimated) LabelChip(stringResource(R.string.plans_estimated_chip))
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            entryLine(entry),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** From how far off a whole recipe a meal's portion is worth a mark of its own. */
private const val AUTO_BALANCED_FROM = 0.05

/** How near its target a day stays as it is, as a share of the target: the API does not fit it then. */
private const val DAY_ON_TARGET = 0.02

/** The meal of the day before that this one was parted from, in a plan made for cooking ahead: a press brings the two together again. */
private fun partedFrom(plan: MealPlan, dayBefore: PlanDay?, meal: Meal, today: String): Meal? {
    if (!plan.cooksAhead || meal.cookAhead != null || dayBefore == null || dayBefore.date < today || dayBefore.skipped) return null
    return dayBefore.meals.firstOrNull {
        it.slot == meal.slot && it.recipeId != null && it.recipeId != meal.recipeId && it.cookAhead == null && !it.eaten && !it.custom
    }
}

/** The times of day a meal of one's own can be at, in the order the web application offers them. */
private val CUSTOM_MEAL_SLOTS = listOf("breakfast", "lunch", "dinner", "snack", "second_breakfast")

private val CustomMealFormSaver = listSaver<CustomMealForm, String>(
    save = { listOf(it.name, it.slot, it.calories, it.protein, it.fat, it.carbs, it.servings) },
    restore = { CustomMealForm(it[0], it[1], it[2], it[3], it[4], it[5], it[6]) },
)

/**
 * A meal of the user's own added to a day: its name, its time of day, what one serving holds and
 * how many servings it is, and whether it comes beside the day's [meals] or in the place of one of
 * them. What that adds to the day, and what it does to the day's other meals, is shown before
 * anything is sent, so that nobody types the whole meal as one serving of several. A field the API
 * refused says why, from what came of the meal [sent] last.
 */
@Composable
private fun CustomMealDialog(
    date: String,
    /** A shopping list covers the day. */
    listed: Boolean,
    meals: List<Meal>,
    sent: PlanDetailViewModel.CustomMealSent?,
    sending: Boolean,
    onAdd: (CustomMeal, replaces: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by rememberSaveable(stateSaver = CustomMealFormSaver) { mutableStateOf(CustomMealForm()) }
    // The meal of the day this one is eaten in the place of; none for one beside them.
    var replaces by rememberSaveable { mutableStateOf<String?>(null) }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    val problems = if (showProblems) form.problems() else emptySet()
    val refused = sent?.refused.orEmpty()

    @Composable
    fun problemOf(field: CustomMealField, bound: Bound): String? = when {
        field in problems -> stringResource(R.string.refusal_not_a_number)
        else -> refused[field.path]?.let { refusalText(it, bound) }
    }

    @Composable
    fun Amount(value: String, label: String, field: CustomMealField, change: (CustomMealForm, String) -> CustomMealForm, hint: String? = null) {
        FormField(
            value = value,
            onValueChange = { typed -> form = change(form, typed) },
            label = label,
            hint = hint,
            problem = problemOf(field, Bound.Number(decimals = 2)),
            keyboardType = KeyboardType.Decimal,
        )
    }

    // A window over the whole screen: the form is long, and an open keyboard leaves room for little of it.
    ScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(stringResource(R.string.plans_custom_modal_title), style = MaterialTheme.typography.titleLarge)
                Text(formatDayDate(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = form.name,
                    onValueChange = { typed -> form = form.copy(name = typed) },
                    label = stringResource(R.string.plans_custom_name),
                    problem = problemOf(CustomMealField.NAME, Bound.Characters),
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences,
                )
                ChoiceField(
                    label = stringResource(R.string.plans_custom_meal_type),
                    options = CUSTOM_MEAL_SLOTS,
                    selected = form.slot,
                    optionLabel = { mealTypeLabel(it) },
                    onSelect = { slot -> form = form.copy(slot = slot) },
                )
                val today = LocalDate.now().toString()
                ChoiceField(
                    label = stringResource(R.string.plans_custom_place),
                    options = listOf<String?>(null) + meals.map { it.id },
                    selected = replaces,
                    optionLabel = { id ->
                        meals.firstOrNull { it.id == id }?.let { meal -> stringResource(R.string.plans_custom_instead, mealTypeLabel(meal.slot), mealName(meal)) }
                            ?: stringResource(R.string.plans_custom_beside)
                    },
                    onSelect = { id -> replaces = id },
                )
                Text(stringResource(R.string.plans_custom_per_serving), style = MaterialTheme.typography.titleSmall)
                Hint(stringResource(R.string.plans_custom_macros_optional))
                Amount(form.calories, stringResource(R.string.plans_custom_calories), CustomMealField.CALORIES, { f, v -> f.copy(calories = v) })
                Amount(form.protein, stringResource(R.string.plans_custom_protein), CustomMealField.PROTEIN, { f, v -> f.copy(protein = v) })
                Amount(form.fat, stringResource(R.string.plans_custom_fat), CustomMealField.FAT, { f, v -> f.copy(fat = v) })
                Amount(form.carbs, stringResource(R.string.plans_custom_carbs), CustomMealField.CARBS, { f, v -> f.copy(carbs = v) })
                Amount(
                    form.servings,
                    stringResource(R.string.plans_custom_servings),
                    CustomMealField.SERVINGS,
                    { f, v -> f.copy(servings = v) },
                    hint = stringResource(R.string.plans_custom_servings_hint),
                )
                val adds = form.adds()
                Text(
                    if (adds != null && !form.whole) {
                        stringResource(R.string.plans_custom_total_estimated, formatNumber(adds.calories))
                    } else if (adds != null) {
                        stringResource(
                            R.string.plans_custom_total,
                            formatNumber(adds.calories),
                            formatNumber(adds.protein),
                            formatNumber(adds.fat),
                            formatNumber(adds.carbs),
                        )
                    } else {
                        stringResource(R.string.plans_custom_total_pending)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                // What adding it does to the day, said before it is added.
                Text(
                    stringResource(if (replaces != null) R.string.plans_custom_instead_note else R.string.plans_custom_beside_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                meals.firstOrNull { it.id == replaces }?.let { meal -> apartNote(meal, date, today)?.let { Hint(it) } }
                ListNote(listed)
                sent?.error?.let { ErrorBanner(errorMessage(it)) }
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = {
                        val meal = form.meal()
                        if (meal != null) onAdd(meal, replaces) else showProblems = true
                    },
                    enabled = !sending,
                ) { Text(stringResource(R.string.plans_custom_submit)) }
            }
        }
    }
}

/**
 * The ways to change a meal, behind one button named by the meal, as the web folds them on a narrow
 * screen: any recipe of the plan's diet, a favourite picked by hand, a model's pick (off, and saying
 * why, on an account without a model), one with the profile's favourite ingredients, or one of its
 * ingredients substituted, where it [canSwap]; and taking it off its day, whichever it is.
 */
@Composable
private fun MealChanges(
    meal: Meal,
    parted: Meal?,
    date: String,
    busy: Boolean,
    aiOn: Boolean,
    canSwap: Boolean,
    onChange: (SwapAsk) -> Unit,
    onPickFavorite: () -> Unit,
    onSubstitute: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !busy) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.plans_action_for, stringResource(R.string.plans_change_meal), mealName(meal)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(action: () -> Unit) {
                open = false
                action()
            }
            if (canSwap) {
                parted?.let { before ->
                    val dayBefore = LocalDate.parse(date).minusDays(1).toString()
                    Change(stringResource(R.string.plans_cooking_same, weekdayShort(dayBefore)), null) {
                        pick { onChange(SwapAsk("favorite", favoriteRecipeId = before.recipeId)) }
                    }
                }
                Change(stringResource(R.string.plans_swap), null) { pick { onChange(SwapAsk("random")) } }
                Change("${stringResource(R.string.plans_swap_to_fav)} ★", stringResource(R.string.plans_tooltip_swap_to_fav)) { pick(onPickFavorite) }
                Change(
                    stringResource(R.string.plans_ai_swap),
                    if (aiOn) stringResource(R.string.plans_tooltip_ai_swap) else aiOffText(),
                    enabled = aiOn,
                ) { pick { onChange(SwapAsk("ai")) } }
                Change(stringResource(R.string.plans_swap_fav_ingr), stringResource(R.string.plans_tooltip_swap_by_fav)) { pick { onChange(SwapAsk("favorite_ingredients")) } }
                Change("${stringResource(R.string.plans_substitute_ingr)} ⇄", stringResource(R.string.plans_tooltip_substitute)) { pick(onSubstitute) }
            }
            Change(stringResource(R.string.plans_remove), null) { pick(onRemove) }
        }
    }
}

/** A way to change a meal, with what it does under its name where the name alone does not say it. */
@Composable
private fun Change(label: String, hint: String?, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        onClick = onClick,
        enabled = enabled,
    )
}

/**
 * The profile's favourites a meal can be swapped to: those of the meal's time of day and the plan's
 * diet, or all of them where the user asks, each saying where it is of another time of day or off
 * the diet. Allergens, skipped ingredients and the mark to avoid hold either way; the API sees to it.
 */
@Composable
private fun FavoritePicker(meal: Meal, planDiet: String, liked: List<MarkedRecipe>, onPick: (recipeId: String, showingAll: Boolean) -> Unit, onDismiss: () -> Unit) {
    var showingAll by rememberSaveable { mutableStateOf(false) }
    val offered = liked.filter { showingAll || (meal.slot in it.mealTypes && fitsDiet(it.dietTags, planDiet)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_tooltip_swap_to_fav)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckRow(label = stringResource(R.string.plans_show_all_favorites), checked = showingAll, onChange = { showingAll = it })
                if (offered.isEmpty()) {
                    Hint(
                        if (showingAll) stringResource(R.string.plans_no_favorites)
                        else stringResource(R.string.plans_no_favorites_for_slot, mealTypeLabel(meal.slot)),
                    )
                }
                offered.forEach { favorite ->
                    val times = favorite.mealTypes.map { mealTypeLabel(it) }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(favorite.recipeId, showingAll) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(favorite.title, style = MaterialTheme.typography.bodyMedium)
                        if (meal.slot !in favorite.mealTypes) LabelChip(times.joinToString(DOT))
                        if (!fitsDiet(favorite.dietTags, planDiet)) CautionChip(stringResource(R.string.plans_off_diet_tag))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** What an offer is, as the web says it: the recipe in bold, its servings, and its kcal in place of the meal's. */
@Composable
private fun offerLine(offer: SwapOffer): AnnotatedString {
    val servings = servingsWord(offer.servings)
    val line = stringResource(
        R.string.plans_offer_line,
        MARK,
        servings,
        formatNumber(offer.after.calories.roundToInt()),
        formatNumber(offer.before.calories.roundToInt()),
    )
    return emphasized(line, offer.recipeTitle)
}

/** The recipe offered in the place of a meal, and what taking it does to the meal and to its day. */
@Composable
private fun SwapOfferDialog(
    offer: SwapOffer,
    /** The days of the plan a shopping list covers, and the day of the meal offered another recipe. */
    listedDates: Set<String>,
    offerDate: String?,
    busy: Boolean,
    onTake: (together: Boolean) -> Unit,
    onAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    var only by rememberSaveable(offer.mealId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_offer_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // A model's own pick says so, with its reason, before it is taken; where a model was asked
                // and the engine picked, the offer says why.
                if (offer.pick != null) AiMark(modelPartText(ModelPart.Picked(offer.reason)), spelledOut = true)
                offer.fallbackReason?.let { reason -> fallbackText(reason)?.let { Hint(it, MaterialTheme.colorScheme.tertiary) } }
                Text(offerLine(offer), style = MaterialTheme.typography.bodyMedium)
                Hint(
                    stringResource(
                        R.string.plans_offer_macros,
                        formatNumber(offer.after.protein.roundToInt()),
                        formatNumber(offer.after.fat.roundToInt()),
                        formatNumber(offer.after.carbs.roundToInt()),
                    ),
                )
                // The other meals are fitted to the day only where it comes out away from its target.
                Hint(
                    stringResource(
                        if (abs(offer.dayAfter.calories - offer.calorieTarget) > offer.calorieTarget * DAY_ON_TARGET) R.string.plans_offer_day else R.string.plans_offer_day_after,
                        formatNumber(offer.dayAfter.calories.roundToInt()),
                        formatNumber(offer.calorieTarget.roundToInt()),
                    ),
                )
                offer.targetMacros?.let { target ->
                    Hint(
                        stringResource(
                            R.string.plans_day_macros_of,
                            formatNumber(offer.dayAfter.protein.roundToInt()),
                            formatNumber(target.protein.roundToInt()),
                            formatNumber(offer.dayAfter.fat.roundToInt()),
                            formatNumber(target.fat.roundToInt()),
                            formatNumber(offer.dayAfter.carbs.roundToInt()),
                            formatNumber(target.carbs.roundToInt()),
                        ),
                    )
                }
                CookingNote(offer.cooking, only, onOnly = { only = it })
                if (offer.outsideLimits.isNotEmpty()) Hint(stringResource(R.string.plans_offer_outside, limitNames(offer.outsideLimits)), MaterialTheme.colorScheme.tertiary)
                val together = if (only) emptyList() else offer.cooking?.changes.orEmpty().map { it.date }
                ListNote(
                    listed = (listOfNotNull(offerDate) + together).any { it in listedDates },
                    several = together.isNotEmpty(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onTake(!only && offer.cooking?.changes?.isNotEmpty() == true) }, enabled = !busy) { Text(stringResource(R.string.plans_offer_take)) }
        },
        dismissButton = {
            Row {
                // A favourite picked by hand is the one the user asked for: there is no other to offer.
                if (offer.ask.strategy != "favorite") {
                    TextButton(onClick = onAnother, enabled = !busy) { Text(stringResource(R.string.plans_offer_another)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/**
 * A meal as planned, over the whole screen, so that what it takes and how to cook it are read in one
 * place: its name and what is marked of it, what it holds in a row, its own lines in the amounts the
 * plan gives it (of the whole cooking on the meal it is cooked at), the steps of its recipe from what
 * the plan holds, what stands against it in words, and the buttons at the foot. The recipe as written
 * opens from there, with one sentence on how far its amounts are from these.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealDialog(
    meal: Meal,
    date: String,
    toCook: MealToCook,
    cooking: List<Pair<Meal, String>>,
    dietType: String,
    onOpenRecipe: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now().toString()
    // The dish cooked at this meal being split into the meals of its cooking, while its window is open.
    var splitting by rememberSaveable { mutableStateOf(false) }
    if (splitting) SplitDialog(portionsOfCooking(cooking), onDismiss = { splitting = false })
    ScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mealName(meal), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${mealTypeLabel(meal.slot)}$DOT${formatDayDate(date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    meal.byModel?.let { AiMark(modelPartText(it), spelledOut = true) }
                    if (meal.custom) LabelChip(stringResource(R.string.plans_custom_chip))
                    if (meal.estimated) LabelChip(stringResource(R.string.plans_estimated_chip))
                    cookChip(meal, date)?.let { PlainChip(it) }
                    if (meal.eaten) AccentChip(stringResource(R.string.plans_eaten))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Figure(formatNumber(meal.nutrition.calories.roundToInt()), "kcal")
                    Figure("${formatNumber(meal.nutrition.protein.roundToInt())}\u00A0g", stringResource(R.string.macros_protein))
                    Figure("${formatNumber(meal.nutrition.fat.roundToInt())}\u00A0g", stringResource(R.string.macros_fat))
                    Figure("${formatNumber(meal.nutrition.carbs.roundToInt())}\u00A0g", stringResource(R.string.macros_carbs))
                }
                meal.label?.let { LabelGrid(it) }
                cookHint(meal, date)?.let { Hint(it) }
                if (cooking.isNotEmpty()) {
                    TextButton(onClick = { splitting = true }) { Text(stringResource(R.string.split_open)) }
                }
                notCookedNote(meal, date, today)?.let { Hint(it, MaterialTheme.colorScheme.tertiary) }
                if (meal.custom) Hint(stringResource(if (meal.estimated) R.string.plans_custom_estimated_note else R.string.plans_custom_macros_note))
                // A meal of a recipe is sent as the window shows it: this meal's amounts, or those of the whole cooking.
                if (meal.recipeId != null && toCook.lines.isNotEmpty()) {
                    RecipeShareRow(
                        sharedRecipe(
                            title = meal.name,
                            servings = toCook.servings,
                            lines = toCook.lines.map { SharedLine(it.name, amountLabel(it.quantity, it.unit, it.grams), it.note) },
                            steps = meal.steps,
                            source = meal.recipeSource,
                            byModel = meal.recipeByModel,
                        ),
                    )
                }
                if (toCook.lines.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.recipes_ingredients), style = MaterialTheme.typography.titleMedium)
                        meal.recipeServings?.let { servings -> Hint(portionNote(servings, toCook.servings, toCook.whole)) }
                        toCook.lines.forEach { line ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.name, style = MaterialTheme.typography.bodyMedium)
                                    line.note?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    amountLabel(line.quantity, line.unit, line.grams),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
                if (meal.steps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.recipes_steps), style = MaterialTheme.typography.titleMedium)
                        meal.steps.forEachIndexed { index, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                meal.conflicts.forEach { conflict ->
                    Text(
                        conflictHint(conflict),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (meal.offDiet) Hint(stringResource(R.string.plans_diet_override_hint, dietTypeLabel(dietType)), MaterialTheme.colorScheme.tertiary)
                if (meal.disliked.isNotEmpty()) Hint(stringResource(R.string.plans_disliked_hint, formatList(meal.disliked)))
                if (meal.outsideLimits.isNotEmpty()) Hint(stringResource(R.string.plans_outside_limit_hint, limitNames(meal.outsideLimits)))
                if (meal.onlyRecipe) Hint(stringResource(R.string.plans_only_recipe_hint))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                if (onOpenRecipe != null) Button(onClick = onOpenRecipe) { Text(stringResource(R.string.plans_open_recipe)) }
            }
        }
    }
}

/** A figure of a meal over what it is a figure of. */
@Composable
private fun Figure(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How many servings a meal is, in the forms the languages give whole numbers and fractions. */
@Composable
internal fun servingsWord(servings: Double): String =
    if (servings % 1.0 == 0.0) {
        pluralStringResource(R.plurals.plans_offer_servings, servings.toInt(), formatNumber(servings))
    } else {
        // A count with a fraction takes the form the languages give fractions.
        stringResource(R.string.plans_offer_servings_fraction, formatNumber(servings, fractionDigits = 1))
    }

/** How far the amounts of a meal are from those of its recipe as written. */
@Composable
private fun portionNote(recipeServings: Int, servings: Double, whole: Boolean): String = stringResource(
    if (whole) R.string.plans_portion_whole else R.string.plans_portion_meal,
    pluralStringResource(R.plurals.plans_portion_recipe, recipeServings, recipeServings),
    servingsWord(servings),
)

/** A chip for what is to be borne in mind: a day off its target, a recipe off the diet, a meal outside a limit. */
@Composable
private fun CautionChip(text: String) {
    LabelChip(text = text, container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
}

/** A chip for what a meal is, without anything to mind in it: balanced by a factor, cooked ahead. */
@Composable
private fun PlainChip(text: String) {
    LabelChip(text = text, container = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), content = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun WarningChip(text: String) {
    LabelChip(
        text = text,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun conflictLabel(conflict: String): String = stringResource(
    when (conflict) {
        "excluded_ingredient" -> R.string.plans_conflict_excluded_ingredient
        "avoided" -> R.string.plans_conflict_avoided
        else -> R.string.plans_conflict_allergen
    },
)

@Composable
private fun conflictHint(conflict: String): String = stringResource(
    when (conflict) {
        "excluded_ingredient" -> R.string.plans_conflict_excluded_ingredient_hint
        "avoided" -> R.string.plans_conflict_avoided_hint
        else -> R.string.plans_conflict_allergen_hint
    },
)

/** The week of a plan that [date] is in. */
private fun List<PlanWeek>.weekOf(date: String): PlanWeek? = firstOrNull { it.from <= date && date <= it.to }

/**
 * A week of the plan whose days aim off the plan's target on average, as a change of one day left it:
 * what the average is, and a button that evens the week out, which says beforehand what it does.
 */
@Composable
private fun WeekLine(week: PlanWeek, enabled: Boolean, onEven: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.plans_week_uneven, formatNumber(week.average!!.roundToInt()), formatNumber(week.target.roundToInt())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        OutlinedButton(onClick = onEven, enabled = enabled) { Text(stringResource(R.string.plans_even_week)) }
        Text(
            stringResource(R.string.plans_even_week_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        if (week.listOutOfDate) {
            Text(
                stringResource(R.string.plans_list_out_of_date_days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/**
 * A running plan whose profile's target moved since it was made (a change of activity, the newest
 * weight, a correction of the real expenditure), as the web says it: both targets, and the way to
 * take the profile's from today, said beforehand with what it does to a shopping list.
 */
@Composable
private fun TargetMovedNote(moved: TargetMoved, listed: Boolean, enabled: Boolean, onTake: () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.plans_target_moved, moved.profileTarget, moved.planTarget), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onTake, enabled = enabled) { Text(stringResource(R.string.plans_retarget)) }
            Text(stringResource(R.string.plans_retarget_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListNote(listed, several = true)
        }
    }
}
