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

	@app.route('/benchmark/xpathi-01/BenchmarkTest00542', methods=['GET'])
	def BenchmarkTest00542_get():
		return BenchmarkTest00542_post()

	@app.route('/benchmark/xpathi-01/BenchmarkTest00542', methods=['POST'])
	def BenchmarkTest00542_post():
		RESPONSE = ""

		param = ""
		headers = request.headers.getlist("BenchmarkTest00542")
		
		if headers:
			param = headers[0]

		map29056 = {}
		map29056['keyA-29056'] = 'a-Value'
		map29056['keyB-29056'] = param
		map29056['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map29056['keyB-29056']
		bar = map29056['keyA-29056']

		import lxml.etree
		import helpers.utils

		try:
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

