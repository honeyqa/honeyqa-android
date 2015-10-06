package io.honeyqa.client.network.okio;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Target;

/**
 * @author Kohsuke Kawaguchi
 */
@Retention(CLASS)
@Documented
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface IgnoreJRERequirement {
}