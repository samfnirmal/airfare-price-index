package com.whoisjson.airfarepriceindex;

public class FlightEvent {
    public String airline;
    public String route;
    public String bookingWindow;
    public int dayOffset;
    public String travelDate;
    public double price;
    public long timestamp;

    // Required by Jackson for JSON deserialization
    public FlightEvent() {}

    public FlightEvent(String airline, String route, String bookingWindow, int dayOffset, String travelDate, double price) {
        this.airline = airline;
        this.route = route;
        this.bookingWindow = bookingWindow;
        this.dayOffset = dayOffset;
        this.travelDate = travelDate;
        this.price = price;
        this.timestamp = System.currentTimeMillis();
    }
}