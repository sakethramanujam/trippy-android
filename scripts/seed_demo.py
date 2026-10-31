"""Fill a fresh, empty Trippy server with made-up demo data.

    python scripts/seed_demo.py http://localhost:8000

Everything here is fictional: the user, confirmation numbers, hotels and addresses.
Dates are relative to today, so there is always an upcoming flight with a check-in
countdown, a few past trips and a journey linking two of them.
"""
import json
import sys
import urllib.request
from datetime import datetime, timedelta

API = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000").rstrip("/")
TOK = None
NOW = datetime.now().replace(second=0, microsecond=0)


def call(method, path, body=None):
    headers = {"Content-Type": "application/json"}
    if TOK:
        headers["Authorization"] = "Bearer " + TOK
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, method=method, data=data, headers=headers)
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read() or b"null")


def at(days, hour=0, minute=0):
    d = (NOW + timedelta(days=days)).replace(hour=hour, minute=minute)
    return d.isoformat()


def day(days):
    return at(days)


R = call("POST", "/auth/signup", {"email": "alex@example.com", "password": "demo-password-123!",
                                  "name": "Alex Rivera", "home_airport": "SFO"})
TOK = R["access_token"]
ME = R["user"]["id"]


def trip(title, start, end, desc=None):
    return call("POST", "/trips/", {"title": title, "start_date": start, "end_date": end, "description": desc})["id"]


def flight(t, al, no, a, b, dep, arr, conf, miles, mins, cost, seat, cabin="economy", model=None, maker=None):
    call("POST", f"/trips/{t}/flights/", {
        "airline": al, "flight_number": no, "departure_airport": a, "arrival_airport": b,
        "departure_time": dep, "arrival_time": arr, "confirmation_number": conf, "distance_miles": miles,
        "duration_minutes": mins, "cost": cost, "seat": seat, "cabin_class": cabin, "aircraft_model": model,
        "aircraft_manufacturer": maker, "status": "Landed" if dep < NOW.isoformat() else "Scheduled"})


def stay(t, name, city, country, cin, cout, conf, cost, lat, lng, tz, nights, brand=None, ptype="hotel",
         src="direct", room="King room"):
    call("POST", f"/trips/{t}/lodgings/", {
        "name": name, "city": city, "country": country, "check_in": cin, "check_out": cout,
        "confirmation_number": conf, "cost": cost, "lat": lat, "lng": lng, "check_in_tz": tz, "check_out_tz": tz,
        "nights": nights, "brand": brand, "property_type": ptype, "booking_source": src, "room_type": room,
        "guests": 2, "address": f"1 Demo Street, {city}"})


def rental(t, co, make, model, pc, dc, pt, dt, conf, cost, miles, pcity, dcity, plat, plng, cls="Compact"):
    call("POST", f"/trips/{t}/rental_cars/", {
        "rental_company": co, "vehicle_make": make, "car_model": model, "pickup_location": pc + " Airport",
        "dropoff_location": dc + " Airport", "pickup_code": pc, "dropoff_code": dc, "pickup_time": pt,
        "dropoff_time": dt, "confirmation_number": conf, "cost": cost, "mileage_driven": miles,
        "pickup_city": pcity, "dropoff_city": dcity, "pickup_lat": plat, "pickup_lng": plng,
        "dropoff_lat": plat, "dropoff_lng": plng, "vehicle_class": cls, "currency": "USD"})


def expense(t, amt, desc):
    call("POST", f"/trips/{t}/expenses/", {"amount": amt, "description": desc, "currency": "USD", "payer_id": ME})


# Upcoming: departs tomorrow, so check-in opens within the day.
aus = trip("Austin Tacos & Music", day(1), day(4), "Long weekend on South Congress")
flight(aus, "United", "UA 1580", "SFO", "AUS", at(1, 9, 5), at(1, 14, 40), "DEMO2T", 1504, 215, 248.00, "17C",
       model="737-900", maker="Boeing")
stay(aus, "Congress Demo Hotel", "Austin", "United States", at(1, 16), at(4, 11), "HTL-77120", 612.00,
     30.2500, -97.7495, "America/Chicago", 3)
rental(aus, "Hertz", "Ford", "Mustang Convertible", "AUS", "AUS", at(1, 15, 30), at(4, 10), "HZ-118204", 214.60, 0,
       "Austin", "Austin", 30.1945, -97.6699, "Convertible")
flight(aus, "United", "UA 2277", "AUS", "SFO", at(4, 13, 20), at(4, 15, 10), "DEMO2T", 1504, 230, 0, "17C")
call("POST", f"/trips/{aus}/travelers", {"name": "Jordan Lee"})

sea = trip("Seattle Thanksgiving", day(25), day(29))
flight(sea, "Alaska Airlines", "AS 1127", "SFO", "SEA", at(25, 8, 30), at(25, 10, 40), "DEMOK5", 679, 130, 176.00, "10A")
rental(sea, "Enterprise", "Toyota", "RAV4 Hybrid", "SEA", "SEA", at(25, 11, 15), at(29, 9), "EN-208847", 268.40, 0,
       "Seattle", "Seattle", 47.4502, -122.3088, "Midsize SUV")

lis = trip("Lisbon & Sintra", day(-60), day(-55), "Spring week by the sea")
flight(lis, "TAP Air Portugal", "TP 238", "SFO", "LIS", at(-60, 13, 5), at(-59, 8, 40), "DEMO7Q", 5672, 695, 912.40,
       "24A", model="A330-900neo", maker="Airbus")
stay(lis, "Casa Azulejo Demo Hotel", "Lisbon", "Portugal", at(-59, 15), at(-55, 11), "HTL-48213", 784.00,
     38.7139, -9.1394, "Europe/Lisbon", 4)
rental(lis, "Sixt", "Fiat", "500 Hybrid", "LIS", "LIS", at(-57, 9), at(-56, 18), "SX-551902", 96.50, 74,
       "Lisbon", "Lisbon", 38.7742, -9.1342, "Mini")
call("POST", f"/trips/{lis}/travelers", {"name": "Jordan Lee"})
expense(lis, 64.0, "Pastries and coffee")
expense(lis, 38.0, "Tram day passes")
expense(lis, 120.0, "Dinner in Alfama")

mad = trip("Madrid", day(-55), day(-52))
flight(mad, "Iberia", "IB 3107", "LIS", "MAD", at(-55, 14, 20), at(-55, 16, 40), "DEMO8R", 318, 80, 142.00, "12C",
       model="A320neo", maker="Airbus")
stay(mad, "Hotel Prado Demo", "Madrid", "Spain", at(-55, 15), at(-52, 11), "HTL-90377", 540.00,
     40.4138, -3.6921, "Europe/Madrid", 3)
flight(mad, "United", "UA 5821", "MAD", "SFO", at(-52, 11, 30), at(-52, 14, 55), "DEMO9S", 5826, 745, 0, "31F",
       model="787-9", maker="Boeing")

tyo = trip("Tokyo in Bloom", day(-150), day(-143))
flight(tyo, "Japan Airlines", "JL 1", "SFO", "HND", at(-150, 11, 55), at(-149, 15, 35), "DEMOJ1", 5130, 660, 1240.00,
       "41K", model="777-300ER", maker="Boeing")
stay(tyo, "Sakura Demo Inn", "Tokyo", "Japan", at(-149, 16), at(-143, 10), "HTL-22018", 1302.00,
     35.6717, 139.7650, "Asia/Tokyo", 6)
flight(tyo, "Japan Airlines", "JL 2", "HND", "SFO", at(-143, 18, 10), at(-143, 11), "DEMOJ1", 5130, 590, 0, "41K")
expense(tyo, 210.0, "Ramen crawl")

den = trip("Denver Ski Weekend", day(-280), day(-277))
flight(den, "Southwest", "WN 1204", "SFO", "DEN", at(-280, 7, 10), at(-280, 10, 45), "DEMOW4", 967, 155, 189.00, "Open")
rental(den, "Hertz", "Subaru", "Outback", "DEN", "DEN", at(-280, 11, 30), at(-277, 8), "HZ-730114", 312.80, 286,
       "Denver", "Denver", 39.8494, -104.6738, "Midsize SUV")
stay(den, "Pine Ridge Demo Cabin", "Breckenridge", "United States", at(-280, 16), at(-277, 10), "HMDEMO4821", 690.00,
     39.4817, -106.0384, "America/Denver", 3, ptype="rental", src="Airbnb", room="Entire cabin")
flight(den, "Southwest", "WN 2210", "DEN", "SFO", at(-277, 12, 15), at(-277, 14, 5), "DEMOW4", 967, 170, 0, "Open")

chi = trip("Chicago Architecture Weekend", day(-420), day(-417))
flight(chi, "Alaska Airlines", "AS 344", "SFO", "ORD", at(-420, 6), at(-420, 12, 20), "DEMOA3", 1846, 260, 238.00, "8D")
stay(chi, "Riverwalk Demo Hotel", "Chicago", "United States", at(-420, 15), at(-417, 11), "HTL-30951", 645.00,
     41.8881, -87.6298, "America/Chicago", 3)
flight(chi, "Alaska Airlines", "AS 351", "ORD", "SFO", at(-417, 17, 30), at(-417, 20, 5), "DEMOA3", 1846, 275, 0, "8D")

call("POST", "/journeys/", {"title": "Lisbon → Madrid", "trip_ids": [lis, mad]})
call("PUT", f"/trips/{aus}/pin")
print(json.dumps({"aus": aus, "sea": sea, "lis": lis, "mad": mad, "tyo": tyo, "den": den, "chi": chi}))
