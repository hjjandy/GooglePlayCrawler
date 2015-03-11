package edu.purdue.cs.googleplaycrawler;

public class SimplePair<T1, T2> {
	public T1 first;
	public T2 second;

	public SimplePair(T1 t1, T2 t2) {
		first = t1;
		second = t2;
	}

	public SimplePair(T1 t1) {
		this(t1, null);
	}
}
