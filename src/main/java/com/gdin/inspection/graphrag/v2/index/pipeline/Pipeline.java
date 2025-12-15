package com.gdin.inspection.graphrag.v2.index.pipeline;

import lombok.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Pipeline<C> implements Iterable<Pipeline.Step<C>> {

    @Value
    public static class Step<C> {
        String name;
        WorkflowFunction<C> fn;
    }

    private final List<Step<C>> steps = new ArrayList<>();

    public Pipeline<C> add(String name, WorkflowFunction<C> fn) {
        steps.add(new Step<>(name, fn));
        return this;
    }

    public void remove(String name) {
        steps.removeIf(s -> s.getName().equals(name));
    }

    @Override
    public Iterator<Step<C>> iterator() {
        return steps.iterator();
    }
}
