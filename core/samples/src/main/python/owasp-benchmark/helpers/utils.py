import subprocess

RES_DIR = './helpers/resources'
TESTFILES_DIR = './testfiles'

mysession = {}

sharedstr = 'default shared string'

commonHeaderNames = [
	"accept",
	"accept-encoding",
	"accept-language",
	"cache-control",
	"connection",
	"content-length",
	"content-type",
	"cookie",
	"host",
	"origin",
	"pragma",
	"referer",
	"sec-ch-ua",
	"sec-ch-ua-mobile",
	"sec-ch-ua-platform",
	"sec-fetch-dest",
	"sec-fetch-mode",
	"sec-fetch-site",
	"user-agent",
	"x-requested-with"
]

html_entity_names = {
	34: 'quot',
	38: 'amp',
	60: 'lt',
	62: 'gt'
# ... to be completed, i guess (though we below encode a ton of characters that don't usually get escaped...)
}

# implementation of ESAPI escape
def escape_for_html(s: str):
	immune = [',', '.', '-', '_', ' ']
	ret = ''

	for c in s:
		if c.isalnum() or c in immune:
			ret += c
		elif (ord(c) <= 0x1f and (c != '\t') and (c != '\n') and (c != '\r')) or (0x7f <= ord(c) and ord(c) <= 0x9f):
			# illegal character
			ret += '&#xfffd;'
		elif ord(c) in html_entity_names:
			ret += f'&{html_entity_names[ord(c)]};'
		else:
			ret += f'&#x{hex(ord(c))[2:]};'

	return ret



def commandOutput(proc: subprocess.CompletedProcess):
	ret = '''<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">
<html>
<head>
<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">
</head>
<body>
<p>
Here is the standard output of the command:<br>'''
	newline_escaped = escape_for_html('\n')
	ret += escape_for_html(proc.stdout).replace(newline_escaped, '<br>')
	ret += "<br>Here is the std err of the command (if any):<br>"
	ret += escape_for_html(proc.stderr).replace(newline_escaped, '<br>')
	return ret

# javax httpservletwhatever returns either of http args (?x=y) or form data parameters through one method. Here we are.
def get_parameter(request, param):
	ret = request.args.get(param)
	if not ret:
		ret = request.form.get(param)
	return ret

def get_parameter_list(request, param):
	return request.args.getlist(param) + request.form.getlist(param)

# class used in deserialization test cases as an exploit payload
class Injected:
	def __init__(self, text, othertext):
		pass

	def __getitem__(self, index):
		if index == "text":
			return self.othertext
		return None
