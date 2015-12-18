      # launch in background (ensuring no streams open), and record PID to file
      nohup python -m SimpleHTTPServer ${PORT:-8020} < /dev/null > output.txt 2>&1 &
      echo $! > ${PID_FILE:-pid.txt}
      sleep 5
      ps -p `cat ${PID_FILE:-pid.txt}`
      if [ $? -ne 0 ] ; then
        cat output.txt
        echo WARNING: python web server not running
        exit 1
      fi

