package com.feralenterprises.raccoon.playtest.r02.shared;

import javax.swing.JComponent;

/**
 * Creates one route's page body. Called on the EDT, once per account scope (the shell discards account pages on a
 * scope change and asks again). The body may implement the framework's {@code RouteAware} (typed payloads) and
 * {@code LocalBack} (detail levels), and {@code AccountBound}/{@code Disposable} for cleanup.
 */
@FunctionalInterface
public interface PanelFactory {
	JComponent create(RouteContext context);
}
