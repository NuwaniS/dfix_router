@TITLE DFIXRTR_BUILD_FILE
rem Optional Arguments: durations, license_start_date, parallal_dfix_count
rem Default Values: durations = 12(Months), license_start_date = Build Date, parallal_dfix_count=1
mvn clean install -Dallowed_ip=127.0.0.1,127.0.0.1 -Dallowed_sessions=5 [-Ddurations=*,5] [-Dlicense_start_date=2017-10-25] [-Dparallal_dfix_count=1]
@PAUSE