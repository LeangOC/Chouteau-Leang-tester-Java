package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;


    public class FareCalculatorService {
        public void calculateFare(Ticket ticket) {
            calculateFare(ticket, false); //Appelle avec discount à false par défaut

        }

        public void calculateFare(Ticket ticket,boolean discount){
            if( (ticket.getOutTime() == null) || (ticket.getOutTime().before(ticket.getInTime())) ){
                throw new IllegalArgumentException("Out time provided is incorrect:"+ticket.getOutTime().toString());
            }

            long InMilliSecondes = ticket.getInTime().getTime();
            long OutMilliSecondes = ticket.getOutTime().getTime();

            //TODO: Some tests are failing here. Need to check if this logic is correct
            double duration = (double) (OutMilliSecondes - InMilliSecondes) / (1000 * 60 * 60);

            if (duration < 0.5) {
                ticket.setPrice(0.0);
                return;
            }
            double rate;
            switch (ticket.getParkingSpot().getParkingType()){
                case CAR: {
                    //ticket.setPrice(duration * Fare.CAR_RATE_PER_HOUR);
                    rate = Fare.CAR_RATE_PER_HOUR;
                    break;
                }
                case BIKE: {
                    //ticket.setPrice(duration * Fare.BIKE_RATE_PER_HOUR);
                    rate = Fare.BIKE_RATE_PER_HOUR;
                    break;
                }
                default: throw new IllegalArgumentException("Unkown Parking Type");
            }
            double price = duration * rate;

            // Appliquer une réduction de 5 % si applicable
            if (discount) {
                price *= 0.95;
            }
            ticket.setPrice(price);
        }
    }

