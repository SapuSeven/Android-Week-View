package com.alamkanak.weekview

import java.util.Calendar

/**
 * Wraps all available [EventsLoader]s to allow for dynamic switching between them.
 */
internal class EventsLoaderWrapper<T>(
    cache: EventsCacheWrapper<T>
) {

    private val cachingEventsLoader = CachingEventsLoader(cache)
    private val pagedEventsLoader = PagedEventsLoader(cache)
    private val legacyEventsLoader = LegacyEventsLoader(cache)

    private var currentEventsLoader: EventsLoader<T> = cachingEventsLoader

    fun get() = currentEventsLoader

    fun onListenerChanged(listener: OnLoadMoreListener?) {
        if (listener != null) {
            pagedEventsLoader.onLoadMoreListener = listener
        }

        currentEventsLoader = if (listener != null) pagedEventsLoader else cachingEventsLoader
    }

    fun onListenerChanged(listener: OnMonthChangeListener<T>?) {
        if (listener != null) {
            legacyEventsLoader.onMonthChangeListener = listener
        }

        currentEventsLoader = if (listener != null) legacyEventsLoader else cachingEventsLoader
    }
}

/**
 * An abstract class that provides functionality to load [WeekViewEvent]s.
 */
internal abstract class EventsLoader<T> {

    protected var shouldRefreshEvents: Boolean = false

    /**
     * Called whenever [WeekView] is drawn to the screen.
     *
     * @param firstVisibleDate The first date that is currently visible
     * @param onRefreshComplete Callback to provide the loaded [WeekViewEvent]s back to the caller
     */
    abstract fun refresh(
        firstVisibleDate: Calendar,
        onRefreshComplete: (List<WeekViewEvent<T>>) -> Unit
    )

    /**
     * Instructs an [EventsLoader] that it should reload events when called the next time.
     */
    fun requireRefresh() {
        shouldRefreshEvents = true
    }
}

/**
 * Represents an [EventsLoader] that doesn't actually reload events, but rather serves the cached
 * events from the [EventsCache]. These events were usually provided via [WeekView.submit].
 */
internal class CachingEventsLoader<T>(
    private val cacheWrapper: EventsCacheWrapper<T>
) : EventsLoader<T>() {

    private val simpleCache: SimpleEventsCache<T>
        get() = cacheWrapper.get() as SimpleEventsCache<T>

    override fun refresh(
        firstVisibleDate: Calendar,
        onRefreshComplete: (List<WeekViewEvent<T>>) -> Unit
    ) {
        val fetchRange = FetchRange.create(firstVisibleDate)
        val eventsInRange = simpleCache[fetchRange]
        onRefreshComplete(eventsInRange)
    }
}

/**
 * Represents an [EventsLoader] that allows for paged loading of events. Whenever it is asked to
 * refresh, it uses the its [OnLoadMoreListener] to request that more events be loaded. Unlike
 * [LegacyEventsLoader], it does not return the list of [WeekViewEvent]s to the caller.
 */
internal class PagedEventsLoader<T>(
    private val cacheWrapper: EventsCacheWrapper<T>
) : EventsLoader<T>() {

    lateinit var onLoadMoreListener: OnLoadMoreListener

    private val pagedCache: PagedEventsCache<T>
        get() = cacheWrapper.get() as PagedEventsCache<T>

    override fun refresh(
        firstVisibleDate: Calendar,
        onRefreshComplete: (List<WeekViewEvent<T>>) -> Unit
    ) {
        val fetchRange = FetchRange.create(firstVisibleDate)
        val needsRefresh = pagedCache.isEmpty || shouldRefreshEvents || fetchRange !in pagedCache

        if (needsRefresh) {
            val periods = determinePeriodsToFetch(fetchRange)
            fetchPeriods(periods)
        } else {
            onRefreshComplete(pagedCache.allEvents)
        }
    }

    private fun determinePeriodsToFetch(
        fetchRange: FetchRange
    ) = fetchRange.periods.filter { pagedCache[it] == null }

    private fun fetchPeriods(periods: List<Period>) {
        periods.forEach { period ->
            onLoadMoreListener.onLoadMore(period.startDate, period.endDate)
        }
    }
}

/**
 * Represents an [EventsLoader] that allows for paged loading of events. Whenever it is asked to
 * refresh, it uses the its [OnMonthChangeListener] to request events for any particular month.
 * Unlike [PagedEventsLoader], it does return the list of [WeekViewEvent]s to the caller.
 */
internal class LegacyEventsLoader<T>(
    private val cacheWrapper: EventsCacheWrapper<T>
) : EventsLoader<T>() {

    lateinit var onMonthChangeListener: OnMonthChangeListener<T>

    private val pagedCache: PagedEventsCache<T>
        get() = cacheWrapper.get() as PagedEventsCache<T>

    override fun refresh(
        firstVisibleDate: Calendar,
        onRefreshComplete: (List<WeekViewEvent<T>>) -> Unit
    ) {
        val fetchRange = FetchRange.create(firstVisibleDate)
        val needsRefresh = pagedCache.isEmpty || shouldRefreshEvents || fetchRange !in pagedCache

        if (needsRefresh) {
            prepareCache(fetchRange)
            val periods = determinePeriodsToFetch(fetchRange)
            val events = fetchPeriods(periods)
            onRefreshComplete(events)
        } else {
            onRefreshComplete(pagedCache.allEvents)
        }
    }

    private fun prepareCache(fetchRange: FetchRange) {
        if (shouldRefreshEvents) {
            shouldRefreshEvents = false
            pagedCache.clear()
        }

        pagedCache.adjustToFetchRange(fetchRange)
    }

    private fun determinePeriodsToFetch(
        fetchRange: FetchRange
    ) = fetchRange.periods.filter { pagedCache[it] == null }

    private fun fetchPeriods(periods: List<Period>): List<WeekViewEvent<T>> {
        val results = mutableListOf<WeekViewEvent<T>>()

        for (period in periods) {
            val events = fetchPeriod(period)
            pagedCache[period] = events
            results += events
        }

        return results
    }

    private fun fetchPeriod(
        period: Period
    ) = onMonthChangeListener
        .onMonthChange(period.startDate, period.endDate)
        .map { it.toWeekViewEvent() }
}