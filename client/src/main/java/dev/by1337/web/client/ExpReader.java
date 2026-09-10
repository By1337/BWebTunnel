package dev.by1337.web.client;

import java.text.ParseException;

final class ExpReader {
    private int ridx;
    private final String exp;
    private final int size;

    public ExpReader(String exp) {
        this.exp = exp;
        size = exp.length();
    }


    public char next() {
        if (ridx < size) {
            return exp.charAt(ridx++);
        }
        ridx++;
        return '\0';
    }

    public boolean hasNext() {
        return ridx < size;
    }

    public char last() {
        if (size == 0) return '\0';
        return exp.charAt(Math.max(0, Math.min(Math.max(0, ridx), size - 1)));
    }

    public void back() {
        ridx--;
    }

    public int ridx() {
        return ridx;
    }

    public void ridx(int ridx) {
        this.ridx = ridx;
    }

    public String context() {

        return "\n" + exp + "\n" +
                " ".repeat(Math.max(0, ridx)) +
                "^ ridx=" + ridx + ", length=" + exp.length() + "\n";
    }
    public void throwBadInput() throws ParseException {
        throw new ParseException(context(), ridx);
    }
}