package dev.drewr.fmswitch.model.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals

class FirmwareDescriptionTest {
    @Test fun dropsTheMarkdownMarks() {
        assertEquals("Its visualiser paints it", FirmwareDescription.plain("Its **visualiser** paints it"))
    }

    @Test fun keepsALinksTextButNotTheLink() {
        assertEquals(
            "See the site for more.",
            FirmwareDescription.plain("See [the site](https://example.com) for more."),
        )
    }

    @Test fun leavesPlainTextAlone() {
        assertEquals("Drones that breathe: 6-op FM.", FirmwareDescription.plain("Drones that breathe: 6-op FM."))
    }

    @Test fun readsBoldItalicAndCodeAsRuns() {
        assertEquals(
            listOf(
                FirmwareDescription.Run("bold", bold = true),
                FirmwareDescription.Run(" "),
                FirmwareDescription.Run("italic", italic = true),
                FirmwareDescription.Run(" "),
                FirmwareDescription.Run("code", code = true),
            ),
            FirmwareDescription.runs("**bold** _italic_ `code`"),
        )
    }

    @Test fun leavesAnUnmatchedStarAlone() {
        assertEquals("A *star", FirmwareDescription.plain("A *star"))
    }
}
