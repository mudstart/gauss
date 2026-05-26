package io.gauss.vela.model;

/** A single parameter of a generated TypeScript client function. */
public record TsParameter(String name, TsType type) {

    public String render() {
        return name + ": " + type.render();
    }
}
