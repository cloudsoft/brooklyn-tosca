      # install python if not present
      which python || \
        { apt-get update && apt-get install python ; } || \
        { yum update && yum install python ; } || \
        { echo WARNING: cannot install python && exit 1 ; }

