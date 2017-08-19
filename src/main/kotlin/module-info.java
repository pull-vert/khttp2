/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/**
 * Defines the high-level HTTP and WebSocket API.
 * {@Incubating}
 *
 * @moduleGraph
 * @since 9
 */
module khttp2 {
    requires kotlin.stdlib;
    requires jdk.incubator.httpclient;
    exports io.khttp2;
}

