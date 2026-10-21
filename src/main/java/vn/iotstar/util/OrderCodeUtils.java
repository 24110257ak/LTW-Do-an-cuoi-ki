package vn.iotstar.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

public class OrderCodeUtils {

    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 100000);

    public static String generateOrderCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long nextVal = counter.incrementAndGet() % 100000;
        return String.format("ESC-%s-%05d", datePart, nextVal);
    }
}

