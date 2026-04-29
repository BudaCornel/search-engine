package com.buda.searchengine.query.booleanq;

import java.util.List;


public record SqlFragment(String whereClause, List<Object> params) {}
