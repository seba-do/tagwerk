package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.ai.Conversation
import me.ttdrp.tagwerk.ai.PromptText
import me.ttdrp.tagwerk.ai.ProposedTask
import me.ttdrp.tagwerk.ai.Proposals
import me.ttdrp.tagwerk.ai.SuggestedItem
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.FocusClock
import me.ttdrp.tagwerk.model.Grouping
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Priority
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TaskEntry
import me.ttdrp.tagwerk.model.groupDayEntries
import me.ttdrp.tagwerk.model.groupTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LogicTest {
    private val minute = 60_000L

    @Test
    fun `pausierte Zeit zaehlt nicht und der Stand bleibt beim Pausieren stehen`() {
        var s = FocusClock.start("t", 30, nowMs = 0)
        s = FocusClock.pause(s, 10 * minute)
        assertEquals(20 * minute, FocusClock.remainingMs(s, 99 * minute))
        s = FocusClock.resume(s, 20 * minute)
        assertEquals(20 * minute, FocusClock.remainingMs(s, 20 * minute))
        assertEquals(15 * minute, FocusClock.remainingMs(s, 25 * minute))
    }

    @Test
    fun `verlaengern addiert zur Restzeit und Ueberziehen wird negativ angezeigt`() {
        val s = FocusClock.extend(FocusClock.start("t", 10, 0), 5)
        assertEquals(5 * minute, FocusClock.remainingMs(s, 10 * minute))
        assertEquals("+01:00", FocusClock.format(FocusClock.remainingMs(s, 16 * minute)))
    }

    @Test
    fun `aktuelle Teilaufgabe ergibt sich aus der verstrichenen Zeit`() {
        val subs = listOf(
            Subtask(title = "a", minutes = 5),
            Subtask(title = "ohne Zeit"),
            Subtask(title = "b", minutes = 10),
        )
        assertEquals(FocusClock.Stage(0, 2 * minute), FocusClock.currentStage(subs, 3 * minute))
        assertEquals(FocusClock.Stage(2, 9 * minute), FocusClock.currentStage(subs, 6 * minute))
        assertNull(FocusClock.currentStage(subs, 15 * minute))
    }

    @Test
    fun `Teilaufgaben mit Zeit bestimmen die Dauer`() {
        val task = Task(title = "x", minutes = 60, subtasks = listOf(Subtask(title = "a", minutes = 5), Subtask(title = "b", minutes = 7)))
        assertEquals(12, task.effectiveMinutes)
        assertEquals(60, Task(title = "y", minutes = 60).effectiveMinutes)
    }

    @Test
    fun `Gruppierung verliert keine Aufgaben`() {
        val tasks = listOf(
            Task(title = "kurz", minutes = 5, priority = Priority.HIGH),
            Task(title = "lang", minutes = 90, priority = Priority.LOW),
        )
        Grouping.entries.forEach { grouping ->
            assertEquals(2, groupTasks(tasks, grouping).sumOf { it.tasks.size })
        }
        val bySize = groupTasks(tasks, Grouping.SIZE)
        assertEquals("kurz", bySize.first().tasks.single().title)
        assertEquals("lang", bySize.last().tasks.single().title)
    }

    @Test
    fun `feste Uhrzeit schlaegt die Tageszeit und sortiert im Abschnitt`() {
        val sections = groupDayEntries(
            listOf(
                Task(title = "spaet", startMinute = 19 * 60, daypart = Daypart.MORNING),
                Task(title = "neun", startMinute = 9 * 60),
                Task(title = "acht", startMinute = 8 * 60),
                Task(title = "irgendwann"),
            ),
            emptyList(),
            LocalDate.of(2026, 11, 6),
            ZoneOffset.UTC,
        )
        fun titles(part: Daypart) = sections.first { it.part == part }.entries.map { (it as TaskEntry).task.title }
        assertEquals(listOf("acht", "neun"), titles(Daypart.MORNING))
        assertEquals(listOf("spaet"), titles(Daypart.EVENING))
        assertEquals(listOf("irgendwann"), titles(Daypart.ANYTIME))
        assertEquals(emptyList<String>(), titles(Daypart.AFTERNOON))
    }

    @Test
    fun `Vorschlaege des Modells werden gelesen und Unbrauchbares verworfen`() {
        val parsed = Proposals.parseTasks(
            mapOf(
                "tasks" to listOf(
                    mapOf("title" to " Steuer machen ", "kind" to "todo", "daypart" to "evening", "minutes" to 9999, "subtasks" to listOf("Belege suchen", " ", 3)),
                    mapOf("title" to "", "kind" to "plan"),
                    "kaputt",
                    mapOf("title" to "Nur Titel"),
                ),
            ),
        )
        assertEquals(2, parsed.size)
        assertEquals("Steuer machen", parsed[0].title)
        assertEquals(Kind.TODO, parsed[0].kind)
        assertEquals(Daypart.EVENING, parsed[0].daypart)
        assertEquals(480, parsed[0].minutes)
        assertEquals(listOf("Belege suchen"), parsed[0].subtasks)
        assertEquals(Kind.PLAN, parsed[1].kind)
        assertEquals(30, parsed[1].minutes)
    }

    @Test
    fun `Emoji kommt aus dem Titel mit neutralem Rueckfall`() {
        assertEquals("🧺", EmojiGuess.forTitle("Wäsche waschen"))
        assertEquals("✨", EmojiGuess.forTitle("xyz"))
    }

    private fun items(vararg minutes: Int?) = minutes.mapIndexed { i, m -> SuggestedItem("s$i", m) }

    @Test
    fun `Schritte werden auf die geplante Dauer angepasst`() {
        val fitted = Proposals.fitToDuration(items(5, 5, 10, 45, 10), 45)
        assertEquals(45, fitted.sumOf { it.minutes!! })
        assertEquals(listOf("s0", "s1", "s2", "s3", "s4"), fitted.map { it.title })
        assertTrue(fitted[3].minutes!! > fitted[2].minutes!!)
    }

    @Test
    fun `Anpassen dehnt zu kurze Schritte und laesst jedem mindestens eine Minute`() {
        assertEquals(60, Proposals.fitToDuration(items(5, 10), 60).sumOf { it.minutes!! })
        val tight = Proposals.fitToDuration(items(30, 1), 10)
        assertEquals(10, tight.sumOf { it.minutes!! })
        assertEquals(1, tight[1].minutes)
    }

    @Test
    fun `Schritte ohne Zeit bleiben beim Anpassen ohne Zeit`() {
        val fitted = Proposals.fitToDuration(items(10, null, 30), 20)
        assertNull(fitted[1].minutes)
        assertEquals(20, fitted.sumOf { it.minutes ?: 0 })
    }

    @Test
    fun `Zerlegen nennt dem Modell die geplante Dauer`() {
        assertTrue(PromptText.breakDownInstruction("Steuer", 45).contains("45 Minuten"))
        assertFalse(PromptText.breakDownInstruction("Steuer", null).contains("Minuten"))
    }

    private val friday = LocalDate.of(2026, 11, 6)

    private fun proposed(kind: Kind, date: LocalDate?, daypart: Daypart = Daypart.EVENING) =
        ProposedTask("x", kind, daypart, 30, emptyList(), date)

    @Test
    fun `Datum im Vorschlag wird gelesen und Unbrauchbares ignoriert`() {
        val parsed = Proposals.parseTasks(
            mapOf(
                "tasks" to listOf(
                    mapOf("title" to "a", "date" to "2026-11-07"),
                    mapOf("title" to "b", "date" to "morgen"),
                    mapOf("title" to "c", "date" to null),
                ),
            ),
        )
        assertEquals(LocalDate.of(2026, 11, 7), parsed[0].date)
        assertNull(parsed[1].date)
        assertNull(parsed[2].date)
    }

    @Test
    fun `Plantag folgt dem Vorschlag und faellt nie in die Vergangenheit`() {
        val tomorrow = friday.plusDays(1)
        assertEquals(tomorrow.toEpochDay(), Proposals.planDay(proposed(Kind.PLAN, tomorrow), friday))
        assertEquals(friday.toEpochDay(), Proposals.planDay(proposed(Kind.PLAN, null), friday))
        assertEquals(friday.toEpochDay(), Proposals.planDay(proposed(Kind.PLAN, friday.minusDays(3)), friday))
        assertNull(Proposals.planDay(proposed(Kind.TODO, tomorrow), friday))
    }

    @Test
    fun `Karte nennt den Tag nur wenn er nicht heute ist`() {
        assertEquals("Plan, Abend", Proposals.placeLabel(proposed(Kind.PLAN, null), friday))
        assertEquals("Plan, morgen, Abend", Proposals.placeLabel(proposed(Kind.PLAN, friday.plusDays(1)), friday))
        assertTrue(Proposals.placeLabel(proposed(Kind.PLAN, friday.plusDays(5)), friday).contains("11."))
        assertEquals("Liste", Proposals.placeLabel(proposed(Kind.TODO, friday.plusDays(1)), friday))
    }

    @Test
    fun `Kontext nennt Wochentag und kommende Termine`() {
        val text = PromptText.chatContext(
            listOf(
                Task(title = "Heute", epochDay = friday.toEpochDay()),
                Task(title = "Spaeter", epochDay = friday.toEpochDay() + 2),
                Task(title = "Gestern", epochDay = friday.toEpochDay() - 1),
                Task(title = "Liste", kind = Kind.TODO),
            ),
            friday,
        )
        assertTrue(text.contains("Freitag"))
        assertTrue(text.contains("2026-11-06"))
        assertTrue(text.contains("Heute (30 Min., heute geplant)"))
        assertTrue(text.contains("Spaeter (30 Min., geplant für 2026-11-08)"))
        assertTrue(text.contains("Liste (30 Min., auf der Liste)"))
        assertFalse(text.contains("Gestern"))
    }

    @Test
    fun `Entscheidung waehrend laufender Anfrage geht nicht verloren`() {
        val conversation = Conversation()
        conversation.commit(conversation.outgoing(), emptyList(), listOf("t1"))
        val inFlight = conversation.outgoing()
        conversation.recordDecision("t1", "übernommen")
        conversation.commit(inFlight, emptyList(), emptyList())
        assertEquals(listOf("übernommen"), conversation.outgoing().notes)
    }
}
