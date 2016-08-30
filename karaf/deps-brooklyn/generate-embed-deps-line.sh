
# exclude woodstox because it breaks xml input factory detection - spring then looks for MXParserFactory
# exclude commons/collections because spring needs a different version

tail +2 DEPENDENCIES.txt  | sed 's/^[^(a-z)]*//' | sed s/:.*// | sort | uniq | \
  grep -v woodstox | \
  grep -v org.apache.commons | \
  grep -v commons-collections | \
  awk '{printf("|%s",$1)}' | sed 's/|/*;groupId=!/'

