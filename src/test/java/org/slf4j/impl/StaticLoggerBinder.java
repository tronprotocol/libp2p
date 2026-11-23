package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.helpers.SubstituteLoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

/** Test-classpath binding discovered by SLF4J 1.7. */
public final class StaticLoggerBinder implements LoggerFactoryBinder {

  public static final String REQUESTED_API_VERSION = "1.7";
  private static final StaticLoggerBinder INSTANCE = new StaticLoggerBinder();
  private final SubstituteLoggerFactory factory = new SubstituteLoggerFactory();

  private StaticLoggerBinder() {
    // Named loggers discard events until a test attaches a recorder.
    factory.postInitialization();
  }

  public static StaticLoggerBinder getSingleton() {
    return INSTANCE;
  }

  @Override
  public ILoggerFactory getLoggerFactory() {
    return factory;
  }

  @Override
  public String getLoggerFactoryClassStr() {
    return factory.getClass().getName();
  }
}
