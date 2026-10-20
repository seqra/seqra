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

	@app.route('/benchmark/xpathi-01/BenchmarkTest00550', methods=['GET'])
	def BenchmarkTest00550_get():
		return BenchmarkTest00550_post()

	@app.route('/benchmark/xpathi-01/BenchmarkTest00550', methods=['POST'])
	def BenchmarkTest00550_post():
		RESPONSE = ""

		param = ""
		headers = request.headers.getlist("BenchmarkTest00550")
		
		if headers:
			param = headers[0]

		map16095 = {}
		map16095['keyA-16095'] = 'a-Value'
		map16095['keyB-16095'] = param
		map16095['keyC'] = 'another-Value'
		bar = map16095['keyB-16095']

		import lxml.etree
		import helpers.utils

		try:
			if '\'' in bar:
				RESPONSE += (
					"Employee ID must not contain apostrophes"
				)
				return RESPONSE

			fd = open(f'{helpers.utils.RES_DIR}/employees.xml', 'rb')
			root = lxml.etree.parse(fd)
			query = f'/Employees/Employee[@emplid=\'{bar}\']'
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

