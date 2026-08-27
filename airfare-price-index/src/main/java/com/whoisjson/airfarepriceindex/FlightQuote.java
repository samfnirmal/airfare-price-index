package com.whoisjson.airfarepriceindex;

public class FlightQuote {
    public String route;
    public String bookingWindow;
    public String airline;
    public String totalFare;

    public FlightQuote(String route, String bookingWindow, String airline, String totalFare) {
        this.route = route;
        this.bookingWindow = bookingWindow;
        this.airline = airline;
        this.totalFare = totalFare;
    }

    @Override
    public String toString() {
        return String.format("[%s | %s] %s: %s", route, bookingWindow, airline, totalFare);
    }
}