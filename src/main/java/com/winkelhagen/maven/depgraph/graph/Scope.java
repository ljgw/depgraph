package com.winkelhagen.maven.depgraph.graph;

/**
 * different maven scopes, coupled with color
 * todo: put this in a map somewhere instead of an enum
 */
public enum Scope {
    ROOT("black"), COMPILE("black"), PROVIDED("green"), RUNTIME("blueviolet"), TEST("blue"), SYSTEM("darkgreen"), IMPORT("cyan");

    private String color;

    Scope(String color) {
        this.color = color;
    }


    /**
     * get the scope by its case insensitive name.
     * @param name the name of the Scope
     * @return the Scope
     */
    public static Scope byName(String name){
        for (Scope value : values()){
            if (value.name().equalsIgnoreCase(name)){
                return value;
            }
        }
        return null;
    }

    /**
     * the DOT format color used for this scope type (both vertex and edge)
     * @return
     */
    public String getColor() {
        return color;
    }
}
