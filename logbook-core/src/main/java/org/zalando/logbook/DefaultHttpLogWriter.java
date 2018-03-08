package org.zalando.logbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.slf4j.MarkerFactory.getDetachedMarker;
import static org.zalando.logbook.Origins.translate;

public final class DefaultHttpLogWriter implements HttpLogWriter {

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private final Map<List<String>, Marker> markers = new ConcurrentHashMap<>();

    private final Logger logger;
    private final BooleanSupplier activator;
    private final BiConsumer<Marker, String> consumer;

    public DefaultHttpLogWriter() {
        this(LoggerFactory.getLogger(Logbook.class));
    }

    public DefaultHttpLogWriter(final Logger logger) {
        this(logger, Level.TRACE);
    }

    public DefaultHttpLogWriter(final Logger logger, final Level level) {
        this.logger = logger;
        this.activator = chooseActivator(logger, level);
        this.consumer = chooseConsumer(logger, level);
    }

    private static BooleanSupplier chooseActivator(final Logger logger, final Level level) {
        switch (level) {
            case DEBUG:
                return logger::isDebugEnabled;
            case INFO:
                return logger::isInfoEnabled;
            case WARN:
                return logger::isWarnEnabled;
            case ERROR:
                return logger::isErrorEnabled;
            default:
                return logger::isTraceEnabled;
        }
    }

    private static BiConsumer<Marker, String> chooseConsumer(final Logger logger, final Level level) {
        switch (level) {
            case DEBUG:
                return logger::debug;
            case INFO:
                return logger::info;
            case WARN:
                return logger::warn;
            case ERROR:
                return logger::error;
            default:
                return logger::trace;
        }
    }

    // visible for testing
    Logger getLogger() {
        return logger;
    }

    @Override
    public boolean isActive(final RawHttpRequest request) {
        return activator.getAsBoolean();
    }

    @Override
    public void writeRequest(final Precorrelation<String> precorrelation) {
        final Marker marker = createMarker(precorrelation.getOriginalRequest());
        consumer.accept(marker, precorrelation.getRequest());
    }

    @Override
    public void writeResponse(final Correlation<String, String> correlation) {
        final Marker marker = createMarker(correlation.getOriginalResponse());
        consumer.accept(marker, correlation.getResponse());
    }

    private Marker createMarker(final HttpRequest request) {
        return createMarker("request", translate(request.getOrigin()));
    }

    private Marker createMarker(final HttpResponse response) {
        return createMarker("response", translate(response.getOrigin()), toString(response.getStatus()));
    }

    private String toString(final int status) {
        return (status / 100) + "xx";
    }

    private Marker createMarker(final String... names) {
        return markers.computeIfAbsent(unmodifiableList(asList(names)), this::createMarker);
    }

    private Marker createMarker(final Collection<String> names) {
        return names.stream()
                .map(MarkerFactory::getDetachedMarker)
                .reduce(this::add)
                .orElseThrow(NoSuchElementException::new);
    }

    private Marker add(final Marker parent, final Marker child) {
        final Marker copy = copy(parent);
        copy.add(child);
        return copy;
    }

    private Marker copy(final Marker marker) {
        final Marker copy = getDetachedMarker(marker.getName());

        final Iterator<Marker> iterator = marker.iterator();
        while (iterator.hasNext()) {
            copy.add(iterator.next());
        }

        return copy;
    }

}
