#!/bin/bash

pg_restore -U adsb -h localhost -d adsb_db adsb_db_empty.dump
