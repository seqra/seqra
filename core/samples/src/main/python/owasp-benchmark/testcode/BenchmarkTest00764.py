'''
OWASP Benchmark for Python v0.1

This file is part of the Open Web Application Security Project (OWASP) Benchmark Project.
For details, please see https://owasp.org/www-project-benchmark.

The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, version 3.

The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
PURPOSE. See the GNU General Public License for more details.

  Author: Theo Cartsonis
  Created: 2025
'''

from flask import redirect, url_for, request, make_response, render_template
from helpers.utils import escape_for_html

def init(app):

	@app.route('/benchmark/xpathi-01/BenchmarkTest00764', methods=['GET'])
	def BenchmarkTest00764_get():
		return BenchmarkTest00764_post()

	@app.route('/benchmark/xpathi-01/BenchmarkTest00764', methods=['POST'])
	def BenchmarkTest00764_post():
		RESPONSE = ""

		values = request.args.getlist("BenchmarkTest00764")
		param = ""
		if values:
			param = values[0]

		map34260 = {}
		map34260['keyA-34260'] = 'a-Value'
		map34260['keyB-34260'] = param
		map34260['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map34260['keyB-34260']
		bar = map34260['keyA-34260']

		import lxml.etree
		import helpers.utils
		import io

		try:
			fd = open(f'{helpers.utils.RES_DIR}/employees.xml', 'rb')
			root = lxml.etree.parse(fd)
			strIO = io.StringIO()
			strIO.write('/Employees/Employee[@emplid=\'')
			strIO.write(bar)
			strIO.write('\']')
			query = strIO.getvalue()

			nodes = root.xpath(query)
			node_strings = []
			for node in nodes:
				node_strings.append(' '.join([e.text for e in node]))

			RESPONSE += (
				f'Your XPATH query results are: <br>[ {', '.join(node_strings)} ]'
			)
		except:
			RESPONSE += (
				f'Error parsing XPath Query: \'{escape_for_html(query)}\''
			)

		return RESPONSE

