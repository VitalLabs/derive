#!/usr/bin/env ruby

$runner=ARGV[0]
$compile_res=ARGV[1]

$test_res_file="target/test-results.txt"
$test_msg_file=ENV['HOME']+'/tmp/testingmsg.txt'
$test_js_file="resources/public/test/js/test.js"

def run_runner
  `phantomjs #{$runner} #{$test_js_file}`
end

def save filename, txt
  File.open(filename, 'w') { |file|
    file.write(txt)
  }
end

def rm filename
  File.delete(filename) if File.exist?(filename)
end

def fail_msg count
  if count == 0
    ""
  elsif count == 1
    "A test failed!"
  else
    "#{count} tests failed!"
  end
end

def main
  if( /^Successfully/ =~ $compile_res )
    puts "Running tests..."
    res=run_runner
    save($test_res_file, res)

    if( /(?<f>[0-9]+) failures, (?<e>[0-9]+) errors/m =~ res )
      fails = f.to_i + e.to_i
      if( fails == 0 )
        rm($test_msg_file)
        puts "Tests passed!"
      else
        save($test_msg_file, fail_msg(fails))
        puts "Tests failed: #{res}"
      end
    else
      save($test_msg_file, "Script failed!")
    end
  else
    save($test_res_file, "Compile failed!")
    save($test_msg_file, "Compile failed!")
  end
end


main()
