package org.zalando.logbook;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.zalando.logbook.DefaultLogbook.SimpleCorrelation;
import org.zalando.logbook.DefaultLogbook.SimplePrecorrelation;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Predicate;

import static java.time.Duration.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.zalando.logbook.DefaultHttpLogWriter.Level.DEBUG;
import static org.zalando.logbook.DefaultHttpLogWriter.Level.ERROR;
import static org.zalando.logbook.DefaultHttpLogWriter.Level.INFO;
import static org.zalando.logbook.DefaultHttpLogWriter.Level.TRACE;
import static org.zalando.logbook.DefaultHttpLogWriter.Level.WARN;

public final class DefaultHttpLogWriterLevelTest {

    private interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
    
    static Iterable<Arguments> data() {
        final Logger logger = mock(Logger.class);

        return Arrays.asList(
                Arguments.of(create(logger, TRACE), logger, activator(Logger::isTraceEnabled), consumer(Logger::trace)),
                Arguments.of(create(logger, DEBUG), logger, activator(Logger::isDebugEnabled), consumer(Logger::debug)),
                Arguments.of(create(logger, INFO), logger, activator(Logger::isInfoEnabled), consumer(Logger::info)),
                Arguments.of(create(logger, WARN), logger, activator(Logger::isWarnEnabled), consumer(Logger::warn)),
                Arguments.of(create(logger, ERROR), logger, activator(Logger::isErrorEnabled), consumer(Logger::error))
        );
    }

    private static DefaultHttpLogWriter create(final Logger logger, final DefaultHttpLogWriter.Level trace) {
        return new DefaultHttpLogWriter(logger, trace);
    }

    private static Predicate<Logger> activator(final Predicate<Logger> predicate) {
        return predicate;
    }

    private static TriConsumer<Logger, Marker, String> consumer(final TriConsumer<Logger, Marker, String> consumer) {
        return consumer;
    }
    
    @ParameterizedTest
    @MethodSource("data")
    void shouldBeEnabled(final HttpLogWriter unit, final Logger logger, final Predicate<Logger> isEnabled)
            throws IOException {
        unit.isActive(mock(RawHttpRequest.class));

        isEnabled.test(verify(logger));
    }

    @ParameterizedTest
    @MethodSource("data")
    void shouldLogRequestWithCorrectLevel(final HttpLogWriter unit, final Logger logger,
            @SuppressWarnings("unused") final Predicate<Logger> isEnabled, final TriConsumer<Logger, Marker, String> log)
            throws IOException {
        unit.writeRequest(new SimplePrecorrelation<>("1", "foo", MockHttpRequest.create()));

        final ArgumentCaptor<Marker> captor = ArgumentCaptor.forClass(Marker.class);
        log.accept(verify(logger), captor.capture(), eq("foo"));

        final Marker marker = captor.getValue();
        assertTrue(marker.contains("request"));
        assertTrue(marker.contains("remote"));
    }

    @ParameterizedTest
    @MethodSource("data")
    void shouldLogResponseWithCorrectLevel(final HttpLogWriter unit, final Logger logger,
            @SuppressWarnings("unused") final Predicate<Logger> isEnabled, final TriConsumer<Logger, Marker, String> log)
            throws IOException {
        unit.writeResponse(new SimpleCorrelation<>("1", ZERO, "foo", "bar",
                MockHttpRequest.create(), MockHttpResponse.create()));

        final ArgumentCaptor<Marker> captor = ArgumentCaptor.forClass(Marker.class);
        log.accept(verify(logger), captor.capture(), eq("bar"));

        final Marker marker = captor.getValue();
        assertTrue(marker.contains("response"));
        assertTrue(marker.contains("local"));
        assertTrue(marker.contains("2xx"));
    }

}
