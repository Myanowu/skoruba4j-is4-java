package com.myano.skoruba4j.domain;

import java.util.List;

public record PageResult<T>(int page, int pageSize, int totalCount, List<T> items) {}
