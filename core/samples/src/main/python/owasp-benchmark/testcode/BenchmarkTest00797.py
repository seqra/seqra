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

	@app.route('/benchmark/hash-01/BenchmarkTest00797', methods=['GET'])
	def BenchmarkTest00797_get():
		return BenchmarkTest00797_post()

	@app.route('/benchmark/hash-01/BenchmarkTest00797', methods=['POST'])
	def BenchmarkTest00797_post():
		RESPONSE = ""

		values = request.args.getlist("BenchmarkTest00797")
		param = ""
		if values:
			param = values[0]

		import configparser
		
		bar = 'safe!'
		conf76488 = configparser.ConfigParser()
		conf76488.add_section('section76488')
		conf76488.set('section76488', 'keyA-76488', 'a_Value')
		conf76488.set('section76488', 'keyB-76488', param)
		bar = conf76488.get('section76488', 'keyA-76488')

		import hashlib, base64
		import io, helpers.utils

		input = ''
		if isinstance(bar, str):
			input = bar.encode('utf-8')
		elif isinstance(bar, io.IOBase):
			input = bar.read(1000)

		if len(input) == 0:
			RESPONSE += (
				'Cannot generate hash: Input was empty.'
			)
			return RESPONSE

		hash = hashlib.new('sha384')
		hash.update(input)

		result = hash.digest()
		f = open(f'{helpers.utils.TESTFILES_DIR}/passwordFile.txt', 'a')
		f.write(f'hash_value={base64.b64encode(result)}\n')
		RESPONSE += (
			f'Sensitive value \'{helpers.utils.escape_for_html(input.decode('utf-8'))}\' hashed and stored.'
		)
		f.close()

		return RESPONSE

