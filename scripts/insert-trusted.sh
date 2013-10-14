#!/bin/bash

if [ $# -ne 4 ]
then
	echo "USAGE:  $0 <trust-store> <trust-store-password> <new-cert-file> <new-cert-alias>"
	exit 1
fi

TRUST_STORE="$1"
TRUSTED_PASS="$2"
CERT_FILE="$3"
OUTPUT_ALIAS="$4"
"$GENII_INSTALL_DIR/cert-tool" import -output-keystore="$TRUST_STORE" -output-keystore-pass="$TRUSTED_PASS" -base64-cert-file="$CERT_FILE" -output-alias="$OUTPUT_ALIAS"
