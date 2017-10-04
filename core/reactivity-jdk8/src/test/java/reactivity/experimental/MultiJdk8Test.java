package reactivity.experimental;

import org.junit.Test;

public class MultiJdk8Test {
    @Test
    public void rangeJavaTest() {
        // Works with a nice jdk8 lambda !
        MultiBuilder.fromRange(1, 3)
                .subscribe((value) -> {
                    System.out.println(value);
                    return null;
                });
    }
}