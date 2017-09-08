import os
import sys
import re

def PortSnippet(input_path, output_path):
	# Define regexes for more complicated remappings
	re_FMSettings = re.compile("<FMSettings")
	re_RunInfoPublish = re.compile("RunInfoPublish=\"(?P<RunInfoPublish>true|false)\"")
	re_OfficialRunNumbers = re.compile("OfficialRunNumbers=\"(?P<OfficialRunNumbers>true|false)\"")
	re_NumberOfEvents = re.compile("NumberOfEvents=\"(?P<NumberOfEvents>\d+)\"")

	re_include = re.compile("<include")
	re_include_file = re.compile("file=\"/(?P<file>.+?)\"")
	re_include_version = re.compile("version=\"(?P<version>.+?)\"")

	# Define a simple map for string.replaces
	tag_mapping = {
		"CfgScript":"HCAL_CFGSCRIPT",
		"PIControl":"HCAL_PICONTROL_MULTI",
		"PIControlSingle":"HCAL_PICONTROL_SINGLE",
		"PIControlMulti":"HCAL_PICONTROL_MULTI",
		"ICIControlMulti":"HCAL_ICICONTROL_MULTI",
		"ICIControlSignle":"HCAL_ICICONTROL_Single",
		"TCDSControl":"HCAL_ICICONTROL_MULTI",
		"LPMControl":"HCAL_LPMCONTROL",
		"FedEnableMask":"FED_ENABLE_MASK",
		"AlarmerURL":"HCAL_ALARMER_URL",
	}

	input_snippet = open(input_path, 'r')
	output_snippet_text = ""
	for line in input_snippet:
		# FMSettings
		if re_FMSettings.search(line):
			match_RunInfoPublish = re_RunInfoPublish.search(line)
			if match_RunInfoPublish:
				output_snippet_text += "<HCAL_RUNINFOPUBLISH>{0}</HCAL_RUNINFOPUBLISH>\n".format(match_RunInfoPublish.group("RunInfoPublish"))

			match_OfficialRunNumbers = re_OfficialRunNumbers.search(line)
			if match_OfficialRunNumbers:
				output_snippet_text += "<OFFICIAL_RUN_NUMBERS>{0}</OFFICIAL_RUN_NUMBERS>\n".format(match_OfficialRunNumbers.group("OfficialRunNumbers"))

			match_NumberOfEvents = re_NumberOfEvents.search(line)
			if match_NumberOfEvents:
				output_snippet_text += "<NUMBER_OF_EVENTS>{0}</NUMBER_OF_EVENTS>\n".format(match_NumberOfEvents.group("NumberOfEvents"))

		# xi:includes
		elif re_include.search(line):
			match_include_file = re_include_file.search(line)
			if not match_include_file:
				raise ParsingError("Didn't find file in include line {0}".format(line))
			include_file = match_include_file.group("file").replace("^/", "")
			match_include_version = re_include_version.search(line)
			if not match_include_version:
				raise ParsingError("Didn't find version in include line {0}".format(line))
			include_version = match_include_version.group("version")
			output_snippet_text += "<xi:include parse=\"text\" href=\"{0}/{1}\"/>\n".format(include_file, include_version)

		# Tag replacements/nothing to change
		else:
			for input_tag, output_tag in tag_mapping.iteritems():
				line = line.replace(input_tag, output_tag)
			output_snippet_text += line
	input_snippet.close()

	output_snippet = open(output_path, 'w')
	output_snippet.write(output_snippet_text)
	output_snippet.close()

class ParsingError(Exception):
   def __init__(self, arg):
      self.args = arg


if __name__ == "__main__":
	import argparse
	parser = argparse.ArgumentParser(description="Convert snippets to FM parameter-centric format")
	parser.add_argument("input_path", type=str, help="Input snippet path")
	parser.add_argument("output_path", type=str, help="Output snippet path")
	args = parser.parse_args()

	try:
		PortSnippet(args.input_path, args.output_path)
	except ParsingError,e:
		import time
		print "Failed to parse snippet:"
		print "".join(e.args)
		sys.exit(1)

	#os.system("diff {} {}".format(args.input_path, args.output_path))
	os.system("cat {0}".format(args.output_path))
