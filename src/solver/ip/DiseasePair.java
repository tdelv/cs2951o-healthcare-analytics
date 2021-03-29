package solver.ip;

import java.util.Objects;

public class DiseasePair {
    public int d1, d2;
    public DiseasePair(int d1, int d2) {
        this.d1 = d1;
        this.d2 = d2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiseasePair that = (DiseasePair) o;
        return d1 == that.d1 && d2 == that.d2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(d1, d2);
    }
}
