package pl.syntaxdevteam.plotsx.hooks

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class CleanerXNameFilterTest {
    class Filter {
        fun containsBannedWord(name: String): Boolean = name.contains("forbidden")
    }
    class BrokenFilter {
        fun containsBannedWord(name: String): Boolean = error("Filter unavailable: $name")
    }
    class InvalidFilter {
        fun containsBannedWord(name: String): String = name
    }

    @Test fun rejectsDetectedWordsAndAcceptsCleanNames() {
        assertEquals(false, CleanerXNameFilter.isAllowed(Filter(), "my forbidden plot"))
        assertEquals(true, CleanerXNameFilter.isAllowed(Filter(), "my garden"))
    }

    @Test fun invalidResultDoesNotApproveName() {
        assertNull(CleanerXNameFilter.isAllowed(InvalidFilter(), "my garden"))
    }

    @Test(expected = InvocationTargetException::class)
    fun filterFailureReachesCallerInsteadOfApprovingName() {
        CleanerXNameFilter.isAllowed(BrokenFilter(), "my garden")
    }

    @Test(expected = NoSuchMethodException::class)
    fun incompatibleApiReachesCallerInsteadOfApprovingName() {
        CleanerXNameFilter.isAllowed(Any(), "my garden")
    }
}
